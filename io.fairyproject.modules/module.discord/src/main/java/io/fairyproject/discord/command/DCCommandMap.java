package io.fairyproject.discord.command;

import io.fairyproject.command.BaseCommand;
import io.fairyproject.discord.DCBot;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DCCommandMap {

    @Getter
    private final DCBot bot;
    private final Map<String, BaseCommand> commands;

    public DCCommandMap(DCBot bot) {
        this.bot = bot;
        this.commands = new ConcurrentHashMap<>();
    }

    /**
     * Find the command with command prefix
     *
     * @param name the name of command with prefix like !
     * @return the command, null if not found
     */
    @Nullable
    public BaseCommand findCommand(String name) {
        return this.commands.getOrDefault(name.toLowerCase(), null);
    }

    public void register(BaseCommand command) {
        String prefix;
        final CommandPrefix annotation = command.getAnnotation(CommandPrefix.class);
        if (annotation != null) {
            prefix = annotation.value();
        } else {
            prefix = this.bot.getCommandPrefix();
        }

        for (String commandName : command.getCommandNames()) {
            this.commands.put(prefix + commandName.toLowerCase(), command);
        }
    }

    public void unregister(BaseCommand command) {
        String prefix;
        final CommandPrefix annotation = command.getAnnotation(CommandPrefix.class);
        if (annotation != null) {
            prefix = annotation.value();
        } else {
            prefix = this.bot.getCommandPrefix();
        }

        for (String commandName : command.getCommandNames()) {
            this.commands.remove(prefix + commandName.toLowerCase());
        }
    }

}
