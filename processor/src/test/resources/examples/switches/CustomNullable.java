package examples.switches;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// tag::annotation[]
// A third-party nullability annotation percolate does not recognise out of the box.
@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.CLASS)
public @interface CustomNullable {}
// end::annotation[]
