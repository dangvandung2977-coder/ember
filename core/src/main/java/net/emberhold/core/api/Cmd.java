package net.emberhold.core.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative command binding (spec 01 §7). Annotate a public method on a class
 * registered via {@code EmberApi.commands().register(obj)}. The method's parameter
 * types drive argument resolution and auto tab-completion.
 *
 * Example:
 * <pre>{@code
 * @Cmd(name = "forecast", perm = "ember.forecast.use", playerOnly = false)
 * public String forecast(CommandSender sender, String world) { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cmd {

    /** Command name (must be lowercase; subcommands use dots, e.g. "ember.diag"). */
    String name();

    /** Required permission; empty means no permission check. */
    String perm() default "";

    /** Whether only a Player (not console) may run this command. */
    boolean playerOnly() default false;
}
