package org.viablespark.persistence.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Controls generated writes without excluding a property from reads or schema validation. */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Write {
  boolean insertable() default true;

  boolean updatable() default true;
}
