package dev.sbs.discordapi.listener;

import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.scope.ComponentContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a persistent component interaction handler.
 *
 * <p>
 * Methods annotated with {@code @Component} are discovered at startup by
 * scanning {@link DiscordCommand} subclasses and {@link PersistentComponentListener}
 * subclasses. Each annotated method is registered by its {@link #value custom id}
 * in a global routing map. When a component interaction arrives whose
 * {@code customId} matches the annotation's value, the framework dispatches
 * the event to this method.
 *
 * <p>
 * The annotated method must:
 * <ul>
 *   <li>declare exactly one parameter of a {@link ComponentContext} subtype
 *       (for example {@code ButtonContext}, {@code SelectMenuContext},
 *       {@code ModalContext})</li>
 *   <li>return a {@link org.reactivestreams.Publisher Publisher&lt;Void&gt;}
 *       (typically {@code Mono<Void>})</li>
 * </ul>
 *
 * <p>
 * The component whose custom id matches this value must be present on a
 * {@link dev.sbs.discordapi.response.Response Response} that was sent with
 * {@link dev.sbs.discordapi.response.Response.Builder#isPersistent(boolean) .isPersistent(true)}.
 *
 * @see PersistentComponentListener
 * @see dev.sbs.discordapi.response.PersistentResponse
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Component {

    /** The explicit custom id of the component this method handles. */
    String value();

}
