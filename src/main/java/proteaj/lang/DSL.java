package proteaj.lang;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DSL {
  public String[] priorities() default {};
  public Class<?>[] with() default {};
}
