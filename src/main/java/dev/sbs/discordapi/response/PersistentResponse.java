package dev.sbs.discordapi.response;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.listener.PersistentComponentListener;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a {@link Response} builder for a persistent message,
 * invoked both at creation time (inline from a command's {@code process}
 * method) and at hydration time (from the framework on cache miss after a
 * bot restart).
 *
 * <p>
 * Discovered at startup by scanning {@link DiscordCommand} and
 * {@link PersistentComponentListener} subclasses. Each annotated method is
 * registered against its host class and {@link #value id}; the combination
 * {@code (ownerClass, id)} is the stable identity used to locate and rebuild
 * a persistent Response on demand.
 *
 * <p>
 * The annotated method must:
 * <ul>
 *   <li>declare exactly one parameter of type {@link EventContext EventContext&lt;?&gt;}
 *       (or a more specific subtype) so it can be called with a command
 *       context at creation time and a hydration context at rebuild time</li>
 *   <li>return a {@link Response}</li>
 * </ul>
 *
 * <p>
 * The builder must NOT access {@link dev.sbs.discordapi.context.scope.MessageContext#getResponse()
 * MessageContext#getResponse()} during execution - the method may be invoked
 * before the cache entry exists, and the static parameter type
 * {@code EventContext<?>} does not expose that accessor in any case.
 *
 * @see PersistentComponentListener
 * @see dev.sbs.discordapi.listener.Component
 * @see Response.Builder#isPersistent(boolean)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PersistentResponse {

    /**
     * Optional discriminator when multiple {@code @PersistentResponse} methods
     * live on the same host class. If a class has exactly one annotated
     * method, this value may be left empty.
     */
    String value() default "";

}
