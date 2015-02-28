package phenan.prj.ir

import phenan.prj._
import phenan.prj.decl._
import phenan.prj.state._

import JModifier._

import scala.util._

sealed trait IRModule extends JClass {
  protected val declaration: ModuleDeclaration

  def name: String = file.getPackageName match {
    case Some(pack) => pack + "." + declaration.name
    case None       => declaration.name
  }

  val internalName: String = file.getPackageInternalName match {
    case Some(pack) => pack + "/" + declaration.name
    case None       => declaration.name
  }

  def file: IRFile
}

class IRClass (protected val declaration: ClassDeclaration, val file: IRFile)(implicit state: JState) extends IRModule {
  lazy val modifiers = IRModifiers(declaration.modifiers)

  lazy val typeVariables = resolver.declareTypeVariables(declaration.typeParameters, Map.empty)

  lazy val typeParameters = declaration.typeParameters.map(param => typeVariables(param.name).parameter)

  lazy val superType: Option[IRGenericClassType] = declaration.superClass.flatMap { sup =>
    resolver.classTypeName(sup, typeVariables) match {
      case Success(t) => Some(t)
      case Failure(e) =>
        state.error("type " + sup + " is not found", e)
        None
    }
  }

  lazy val interfaceTypes: List[IRGenericClassType] = declaration.interfaces.flatMap { ifc =>
    resolver.classTypeName(ifc, typeVariables) match {
      case Success(t) => Some(t)
      case Failure(e) =>
        state.error("type " + ifc + " is not found", e)
        None
    }
  }

  lazy val members: List[IRClassMemberDef] = declaration.members.flatMap {
    case InstanceInitializer(block) =>
      List(IRInstanceInitializerDef(block, this))

    case StaticInitializer(block)   =>
      List(IRStaticInitializerDef(block, this))

    case ConstructorDeclaration(cMods, cTypeParams, cParams, cThrows, cBody) =>
      val modifiers = IRModifiers(cMods)
      val typeVars = resolver.declareTypeVariables(cTypeParams, typeVariables)
      val typeParams = cTypeParams.map(param => typeVars(param.name).parameter)
      val dcl = for {
        params <- getIRFormalParameters(cParams, typeVars)
        throws <- resolver.refTypeNames(cThrows, typeVars)
      } yield IRConstructorDef(modifiers, typeParams, typeVars, params, throws, cBody, this)
      dcl match {
        case Success(ir) => List(ir)
        case Failure(e) =>
          state.error("invalid constructor definition", e)
          Nil
      }

    case MethodDeclaration(mMods, mTypeParams, mReturnType, mName, mParams, mThrows, mBody) =>
      val modifiers = IRModifiers(mMods)
      val typeVars =
        if (modifiers.isStatic) resolver.declareTypeVariables(mTypeParams, Map.empty)
        else resolver.declareTypeVariables(mTypeParams, typeVariables)
      val typeParams = mTypeParams.map(param => typeVars(param.name).parameter)
      val dcl = for {
        ret    <- resolver.typeName(mReturnType, typeVars)
        params <- getIRFormalParameters(mParams, typeVars)
        throws <- resolver.refTypeNames(mThrows, typeVars)
      } yield IRMethodDef(modifiers, typeParams, typeVars, ret, mName, params, throws, mBody, this)
      dcl match {
        case Success(ir) => List(ir)
        case Failure(e) =>
          state.error("invalid method definition : " + mName, e)
          Nil
      }

    case FieldDeclaration(fMods, fType, declarators) =>
      val modifiers = IRModifiers(fMods)
      val baseType =
        if (modifiers.isStatic) resolver.typeName(fType, Map.empty)
        else resolver.typeName(fType, typeVariables)
      baseType match {
        case Success(t) => declarators.map { declarator =>
          IRFieldDef(modifiers, declarator.name, resolver.arrayOf(t, declarator.dim), declarator.initializer, this)
        }
        case Failure(e) =>
          state.error("invalid field type", e)
          Nil
      }

    case _ => ???
  }

  override def mod: JModifier = modifiers.flags | accSuper

  override def superClass: Option[JClass] = superType.map(_.erase)
  override def interfaces: List[JClass] = interfaceTypes.map(_.erase)

  override def outerClass: Option[JClass] = None
  override def innerClasses: Map[String, JClass] = ???

  override def methods: List[IRMethodDef] = members.collect { case m: IRMethodDef => m }
  override def fields: List[IRFieldDef] = members.collect { case f: IRFieldDef => f }

  override def classType: JClassType = ???
  override def objectType(typeArgs: List[JValueType]): Option[JObjectType] = ???

  def resolver = file.resolver

  private def getIRFormalParameters (formalParameters: List[FormalParameter], env: Map[String, IRTypeVariable]): Try[List[IRFormalParameter]] = getIRFormalParameters(formalParameters, env, Nil)

  private def getIRFormalParameters (formalParameters: List[FormalParameter], env: Map[String, IRTypeVariable], result: List[IRFormalParameter]): Try[List[IRFormalParameter]] = formalParameters match {
    case param :: rest => resolver.typeName(param.parameterType, env) match {
      case Success(paramType) if param.varArgs =>
        val irp = IRFormalParameter(IRModifiers(param.modifiers), resolver.arrayOf(paramType, param.dim + 1), param.name, true, param.initializer)
        getIRFormalParameters(rest, env, result :+ irp)
      case Success(paramType) =>
        val irp = IRFormalParameter(IRModifiers(param.modifiers), resolver.arrayOf(paramType, param.dim), param.name, false, param.initializer)
        getIRFormalParameters(rest, env, result :+ irp)
      case Failure(e) => Failure(e)
    }
    case Nil => Success(result)
  }
}

sealed trait IRClassMemberDef {
  def declaringClass: IRClass
}

case class IRFieldDef (modifiers: IRModifiers, name: String, genericType: IRGenericType, initializerSnippet: Option[ExpressionSnippet], declaringClass: IRClass) extends JFieldDef with IRClassMemberDef {
  lazy val initializer: Option[IRExpression] = ???

  override def mod: JModifier = modifiers.flags
  override def fieldType: JErasedType = genericType.erase
}

sealed trait IRProcedureDef extends JMethodDef with IRClassMemberDef

case class IRMethodDef (modifiers: IRModifiers, typeParameters: List[IRTypeParameter], typeVariables: Map[String, IRTypeVariable], genericReturnType: IRGenericType, name: String, formalParameters: List[IRFormalParameter], throws: List[IRGenericRefType], bodySnippet: Option[BlockSnippet], declaringClass: IRClass) extends IRProcedureDef {
  lazy val body: Option[IRMethodBody] = ???

  override def mod: JModifier = modifiers.flags
  override def returnType: JErasedType = genericReturnType.erase
  override def paramTypes: List[JErasedType] = formalParameters.map(_.genericType.erase)
  override def exceptions: List[JClass] = throws.map(_.erase)
}

case class IRConstructorDef (modifiers: IRModifiers, typeParameters: List[IRTypeParameter], typeVariables: Map[String, IRTypeVariable], formalParameters: List[IRFormalParameter], throws: List[IRGenericRefType], bodySnippet: BlockSnippet, declaringClass: IRClass) extends IRProcedureDef {
  lazy val body: Option[IRConstructorBody] = ???

  override def mod: JModifier = modifiers.flags
  override def returnType: JErasedType = declaringClass.resolver.voidClass
  override def name: String = "<init>"
  override def paramTypes: List[JErasedType] = formalParameters.map(_.genericType.erase)
  override def exceptions: List[JClass] = throws.map(_.erase)
}

case class IRFormalParameter (modifiers: IRModifiers, genericType: IRGenericType, name: String, varArgs: Boolean, initializerSnippet: Option[ExpressionSnippet]) {
  lazy val initializer: Option[IRExpression] = ???
}

case class IRInstanceInitializerDef (blockSnippet: BlockSnippet, declaringClass: IRClass) extends IRProcedureDef {
  lazy val block: IRBlock = ???

  override def mod: JModifier = JModifier(0)
  override def returnType: JErasedType = declaringClass.resolver.voidClass
  override def name: String = ""
  override def paramTypes: List[JErasedType] = Nil
  override def exceptions: List[JClass] = Nil
}

case class IRStaticInitializerDef (blockSnippet: BlockSnippet, declaringClass: IRClass) extends IRProcedureDef {
  lazy val block: IRBlock = ???

  override def mod: JModifier = JModifier(accStatic)
  override def returnType: JErasedType = declaringClass.resolver.voidClass
  override def name: String = "<clinit>"
  override def paramTypes: List[JErasedType] = Nil
  override def exceptions: List[JClass] = Nil
}
