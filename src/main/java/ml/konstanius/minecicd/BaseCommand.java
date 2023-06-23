package ml.konstanius.minecicd;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;

import static ml.konstanius.minecicd.Messages.sendManagementMessage;
import static ml.konstanius.minecicd.MineCICD.busy;
import static ml.konstanius.minecicd.MineCICD.plugin;

public class BaseCommand implements CommandExecutor {

    public BaseCommand() {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        String subCommand;
        if (args.length == 0) {
            subCommand = "help";
        } else {
            subCommand = args[0];
        }

        if (!sender.hasPermission("minecicd." + subCommand)) {
            sender.sendRichMessage("You do not have permission to use this command");
            return true;
        }

        if (busy) {
            sender.sendRichMessage("The plugin is busy with another command");
            return true;
        }

        // start an empty async task
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (subCommand) {
                    case "pull" -> {
                        try {
                            if (args.length != 1) {
                                sender.sendRichMessage("Invalid arguments. Usage: /MineCICD pull");
                                return;
                            }

                            boolean changes = GitManager.pullRepo();
                            if (changes) {
                                FilesManager.mergeToLocal();
                                sender.sendRichMessage("All changes have been pulled successfully");
                                sendManagementMessage(sender);
                            } else {
                                sender.sendRichMessage("No changes to pull");
                            }
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error pulling repo: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "push" -> {
                        try {
                            if (args.length < 2) {
                                sender.sendRichMessage("Invalid arguments. Usage: /MineCICD push <commit message>");
                                return;
                            }

                            FilesManager.mergeToGit();
                            String commitMessage = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                            GitManager.pushRepo(commitMessage, sender.getName());
                            sender.sendRichMessage("Pushed repo successfully");
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error pushing repo: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "reload" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD reload");
                            return;
                        }

                        Config.reload();

                        try {
                            GitManager.checkoutBranch();
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error checking out branch: " + e.getMessage() + "\nYou might still be able to continue using the plugin");
                            return;
                        }
                        sender.sendRichMessage("Reloaded config successfully");
                    }
                    case "add" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD add <file / 'directory/'> <message> (trailing slash is required for directories)");
                            return;
                        }

                        String file = args[1];
                        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        try {
                            int amount = FilesManager.addPath(file, message, sender.getName());
                            sender.sendRichMessage("Added " + amount + " file(s) successfully");
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error adding file(s): " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "remove" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD remove <file / 'directory/'> <message> (trailing slash is required for directories)");
                            return;
                        }

                        String file = args[1];
                        String message = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                        try {
                            int amount = FilesManager.removePath(file, message, sender.getName());
                            sender.sendRichMessage("Removed " + amount + " file(s) successfully");
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error removing file(s): " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "clone" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD clone");
                            return;
                        }

                        try {
                            GitManager.cloneRepo();
                            FilesManager.mergeToLocal();
                            sender.sendRichMessage("Cloned repo successfully");
                            sendManagementMessage(sender);
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error cloning repo: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "status" -> {
                        if (args.length != 1) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD status");
                            return;
                        }

                        String[] lines = GitManager.getStatus();
                        for (String line : lines) {
                            sender.sendRichMessage(line);
                        }
                    }
                    case "reset" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD reset <commit hash / link>");
                            return;
                        }

                        try {
                            GitManager.reset(args[1]);
                            sender.sendRichMessage("Reset successfully");
                            sendManagementMessage(sender);
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error resetting: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "rollback" -> {
                        if (args.length != 3) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD rollback <dd-MM-yyyy HH:mm:ss>");
                            return;
                        }

                        String date = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                        Calendar calendar = Calendar.getInstance();
                        try {
                            calendar.setTime(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(date));
                        } catch (ParseException e) {
                            sender.sendRichMessage("Invalid date format. Usage: /MineCICD rollback <dd-MM-yyyy HH:mm:ss>");
                            return;
                        }

                        // if future
                        if (calendar.after(Calendar.getInstance())) {
                            sender.sendRichMessage("Invalid date (Is in future). Usage: /MineCICD rollback <dd-MM-yyyy HH:mm:ss>");
                            return;
                        }

                        try {
                            GitManager.rollback(calendar);
                            sender.sendRichMessage("Rolled back successfully");
                            sendManagementMessage(sender);
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error rolling back: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "revert" -> {
                        if (args.length < 3) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD revert <commit hash / link>");
                            return;
                        }

                        try {
                            GitManager.revert(args[1]);
                            sender.sendRichMessage("Reverted successfully");
                            sendManagementMessage(sender);
                        } catch (IOException | GitAPIException e) {
                            sender.sendRichMessage("Error reverting: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                    case "log" -> {
                        if (args.length != 2) {
                            sender.sendRichMessage("Invalid arguments. Usage: /MineCICD log <page>");
                            return;
                        }

                        String[] lines = GitManager.getLog();

                        int page = Integer.parseInt(args[1]);
                        int maxPage = (int) Math.ceil(lines.length / 10.0);
                        if (page > maxPage) {
                            sender.sendRichMessage("Invalid page number. Max page number is " + maxPage);
                            return;
                        }

                        sender.sendRichMessage("===== MineCICD log (" + page + "/" + maxPage + ") =====");
                        for (int i = (page - 1) * 10; i < page * 10; i++) {
                            if (i >= lines.length) {
                                break;
                            }
                            sender.sendRichMessage(lines[i]);
                        }
                        if (maxPage == 1) {
                            sender.sendRichMessage("===== End of log =====");
                        } else {
                            String left = page == 1 ? "<- (Beginning)" : " <blue><u><click:run_command:git log " + (page - 1) + "><- ("+ (page - 1) +") </click></u></blue>";
                            String right = page == maxPage ? "(End) ->" : " <blue><u><click:run_command:git log " + (page + 1) + " >("+ (page + 1) +") -></click></u></blue> > ";
                            sender.sendRichMessage("===== " + left + " | " + right + " =====");
                        }
                    }
                    default -> {
                        sender.sendRichMessage("Invalid subcommand. Valid commands:");
                        sender.sendRichMessage("/MineCICD pull - Pulls the repo from the remote");
                        sender.sendRichMessage("/MineCICD push <commit message> - Pushes the repo to the remote");
                        sender.sendRichMessage("/MineCICD add <file> <message> - Adds a file to the repo");
                        sender.sendRichMessage("/MineCICD clone - Clones the repo from the remote");
                        sender.sendRichMessage("/MineCICD status - Gets the status of the repo");
                        sender.sendRichMessage("/MineCICD reset <commit hash / link> - Resets the current branch to a specific commit");
                        sender.sendRichMessage("/MineCICD revert <commit hash / link> - Reverts a specific commit");
                        sender.sendRichMessage("/MineCICD log <page> - Lists the commits in the repo");
                        sender.sendRichMessage("/MineCICD reload - Reloads the config");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        return true;
    }
}
