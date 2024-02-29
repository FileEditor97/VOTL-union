package union.objects.annotation;

import java.lang.annotation.*;

/**
 * The annotated element could be null under some circumstances.
 *
 * @see union.objects.annotation.NotNull
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface Nullable {}
