package me.obvgreen.command;


import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The set of subcommands {@code /kotl} dispatches to, indexed by every name that reaches them.
 *
 * <p>Held as one object rather than a {@code switch} so that adding a subcommand is a one-line
 * change here and a new class, and so the setup dialog can look a subcommand up by name and run it
 * with a synthetic argument list.</p>
 */
public final class CommandRegistry {

    private final List<KotlSubcommand> subcommands;
    private final Map<String, KotlSubcommand> byName = new LinkedHashMap<>();

    public CommandRegistry() {
        this.subcommands = List.of(
                new SetupCommand(),
                new HelpCommand(this),
                new TopCommand(),
                new ListArenasCommand(),
                new WandCommand(),
                new CreateCommand(),
                new SetPlateCommand(),
                new RespawnCommand(),
                new ToggleCommand(),
                new DeleteCommand());
        subcommands.forEach(this::index);
    }

    private void index(KotlSubcommand subcommand) {
        byName.put(subcommand.name().toLowerCase(Locale.ROOT), subcommand);
        subcommand.aliases().forEach(alias ->
                byName.put(alias.toLowerCase(Locale.ROOT), subcommand));
    }

    /** @return the subcommand {@code name} resolves to, or {@code null} */
    public KotlSubcommand find(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public List<KotlSubcommand> all() {
        return subcommands;
    }

    /** Every registered spelling, primary names first, de-duplicated. */
    public List<String> names() {
        List<String> names = new ArrayList<>();
        subcommands.forEach(subcommand -> names.add(subcommand.name()));
        return names;
    }

    /** The names of every subcommand {@code sender} is allowed to see. */
    public List<String> visibleTo(CommandContext context) {
        List<String> names = new ArrayList<>();
        for (KotlSubcommand subcommand : subcommands) {
            String permission = subcommand.permission();
            if (permission == null || context.has(permission)) {
                names.add(subcommand.name());
            }
        }
        return names;
    }
}
