package io.micronaut.inject.visitor;

public interface VisibileElement extends Element {


    /**
     * @return True if the element is protected.
     */
    boolean isProtected();

    /**
     * @return True if the element is public.
     */
    boolean isPublic();

    /**
     * @return True if the element is abstract.
     */
    default boolean isAbstract() {
        return false;
    }

    /**
     * @return True if the element is static.
     */
    default boolean isStatic() {
        return false;
    }

    /**
     * @return True if the element is private.
     */
    default boolean isPrivate() {
        return !isPublic();
    }

    /**
     * @return True if the element is final.
     */
    default boolean isFinal() {
        return false;
    }
}
