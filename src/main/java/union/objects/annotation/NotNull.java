package union.objects.annotation;

import java.lang.annotation.*;

/**
 * The annotated element must not be null.
 *
 * @see union.objects.annotation.Nullable
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
public @interface NotNull {}
