package com.onarandombox.multiverseinventories;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVPlugin;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.commands.HelpCommand;
import com.onarandombox.multiverseinventories.command.InfoCommand;
import com.onarandombox.multiverseinventories.config.MIConfig;
import com.onarandombox.multiverseinventories.config.SimpleMIConfig;
import com.onarandombox.multiverseinventories.data.FlatfileMIData;
import com.onarandombox.multiverseinventories.data.MIData;
import com.onarandombox.multiverseinventories.group.SimpleWorldGroup;
import com.onarandombox.multiverseinventories.group.SimpleWorldGroupManager;
import com.onarandombox.multiverseinventories.group.WorldGroup;
import com.onarandombox.multiverseinventories.group.WorldGroupManager;
import com.onarandombox.multiverseinventories.listener.MIPlayerListener;
import com.onarandombox.multiverseinventories.listener.MIServerListener;
import com.onarandombox.multiverseinventories.locale.Messager;
import com.onarandombox.multiverseinventories.locale.Messaging;
import com.onarandombox.multiverseinventories.locale.MultiverseMessage;
import com.onarandombox.multiverseinventories.locale.SimpleMessager;
import com.onarandombox.multiverseinventories.migration.ImportManager;
import com.onarandombox.multiverseinventories.permission.MIPerms;
import com.onarandombox.multiverseinventories.profile.PlayerProfile;
import com.onarandombox.multiverseinventories.profile.ProfileManager;
import com.onarandombox.multiverseinventories.profile.WeakProfileManager;
import com.onarandombox.multiverseinventories.profile.WorldProfile;
import com.onarandombox.multiverseinventories.share.Shares;
import com.onarandombox.multiverseinventories.share.SimpleShares;
import com.onarandombox.multiverseinventories.util.MIDebug;
import com.onarandombox.multiverseinventories.util.MILog;
import com.pneumaticraft.commandhandler.CommandHandler;
import me.drayshak.WorldInventories.WorldInventories;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import uk.co.tggl.pluckerpluck.multiinv.MultiInv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.logging.Level;

/**
 * Multiverse-Inventories plugin main class.
 */
public class MultiverseInventories extends JavaPlugin implements MVPlugin, Messaging {

    private final Shares bypassShares = new SimpleShares(true, true, true, true, true);

    private CommandHandler commandHandler;
    private final int requiresProtocol = 9;
    private MultiverseCore core = null;

    private final MIPlayerListener playerListener = new MIPlayerListener(this);
    private final MIServerListener serverListener = new MIServerListener(this);

    private MIConfig config = null;
    private MIData data = null;

    private Messager messager = new SimpleMessager(this);

    private WorldGroupManager worldGroupManager = new SimpleWorldGroupManager();
    private ProfileManager profileManager = new WeakProfileManager(this);
    
    private ImportManager importManager = new ImportManager(this);

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onDisable() {
        // Display disable message/version info
        MILog.info("disabled.", true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void onEnable() {
        MILog.init(this);
        MIPerms.register(this);

        MultiverseCore mvCore;
        mvCore = (MultiverseCore) this.getServer().getPluginManager().getPlugin("Multiverse-Core");
        // Test if the Core was found, if not we'll disable this plugin.
        if (mvCore == null) {
            MILog.severe("Multiverse-Core not found, disabling...");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }
        this.setCore(mvCore);

        if (this.getCore().getProtocolVersion() < this.getRequiredProtocol()) {
            MILog.severe("Your Multiverse-Core is OUT OF DATE");
            MILog.severe("This version of Profiles requires Protocol Level: " + this.getRequiredProtocol());
            MILog.severe("Your of Core Protocol Level is: " + this.getCore().getProtocolVersion());
            MILog.severe("Grab an updated copy at: ");
            MILog.severe("http://bukkit.onarandombox.com/?dir=multiverse-core");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        MIDebug.init(this);

        // Get world groups from config
        this.getGroupManager().setWorldGroups(this.getMIConfig().getWorldGroups());

        // Set debug mode from config
        MILog.setDebugMode(this.getMIConfig().isDebugging());

        try {
            this.getMessager().setLocale(new Locale(this.getMIConfig().getLocale()));
        } catch (IllegalArgumentException e) {
            MILog.severe(e.getMessage());
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize data class
        //this.getProfileManager().setWorldProfiles(this.getData().getWorldProfiles());

        this.getCore().incrementPluginCount();

        // Register Events
        this.registerEvents();

        // Register Commands
        this.registerCommands();

        // Hook plugins that can be imported from
        this.hookImportables();

        // Create initial World Group for first run
        if (this.getMIConfig().isFirstRun()) {
            Collection<MultiverseWorld> mvWorlds = this.getCore().getMVWorldManager().getMVWorlds();
            if (!mvWorlds.isEmpty()) {
                WorldGroup worldGroup = new SimpleWorldGroup("default");
                worldGroup.setShares(new SimpleShares(true, true,
                        true, true, true));
                for (MultiverseWorld mvWorld : mvWorlds) {
                    worldGroup.addWorld(mvWorld.getName());
                }
                this.getMIConfig().updateWorldGroup(worldGroup);
                this.getMIConfig().setFirstRun(false);
                this.getMIConfig().save();
                MILog.info("Created a default group for you containing all of your MV Worlds!");
            } else {
                MILog.info("Could not configure a starter group due to no worlds being loaded into Multiverse-Core.");
                MILog.info("Will attempt again at next start up.");
            }
        }

        // Display enable message/version info
        MILog.info("enabled.", true);
    }

    private void registerEvents() {
        final PluginManager pm = Bukkit.getPluginManager();
        // Event registering goes here
        pm.registerEvent(Event.Type.PLAYER_CHANGED_WORLD, playerListener, Event.Priority.Normal, this);
        pm.registerEvent(Event.Type.PLUGIN_ENABLE, serverListener, Event.Priority.Normal, this);
        pm.registerEvent(Event.Type.PLUGIN_DISABLE, serverListener, Event.Priority.Normal, this);
    }

    private void registerCommands() {
        this.commandHandler = this.getCore().getCommandHandler();
        this.getCommandHandler().registerCommand(new InfoCommand(this));
        for (com.pneumaticraft.commandhandler.Command c : this.commandHandler.getAllCommands()) {
            if (c instanceof HelpCommand) {
                c.addKey("mvi");
            }
        }
    }

    private void hookImportables() {
        final PluginManager pm = Bukkit.getPluginManager();
        Plugin plugin = pm.getPlugin("MultiInv");
        if (plugin != null) {
            this.getImportManager().hookMultiInv((MultiInv) plugin);
        }
        plugin = pm.getPlugin("WorldInventories");
        if (plugin != null) {
            this.getImportManager().hookWorldInventories((WorldInventories) plugin);
        }
    }

    /**
     * @return A class used for managing importing data from other similar plugins.
     */
    public ImportManager getImportManager() {
        return this.importManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        if (!this.isEnabled()) {
            sender.sendMessage("This plugin is Disabled!");
            return true;
        }
        ArrayList<String> allArgs = new ArrayList<String>(Arrays.asList(args));
        allArgs.add(0, command.getName());
        return this.getCommandHandler().locateAndRunCommand(sender, allArgs);
    }

    private CommandHandler getCommandHandler() {
        return this.commandHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void log(Level level, String msg) {
        MILog.log(level, msg, false);
        MIDebug.log(level, msg);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MultiverseCore getCore() {
        return this.core;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCore(MultiverseCore core) {
        this.core = core;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getProtocolVersion() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String dumpVersionInfo(String buffer) {
        buffer += this.logAndAddToPasteBinBuffer("Multiverse-Inventories Version: " + this.getDescription().getVersion());
        buffer += this.logAndAddToPasteBinBuffer("Bukkit Version: " + this.getServer().getVersion());
        buffer += this.logAndAddToPasteBinBuffer("Special Code: FRN001");
        return buffer;
    }

    private String logAndAddToPasteBinBuffer(String string) {
        MILog.info(string);
        return MILog.getString(string + "\n", false);
    }

    /**
     * @return the MIConfig object which contains settings for this plugin.
     */
    public MIConfig getMIConfig() {
        if (this.config == null) {
            // Loads the configuration
            try {
                this.config = new SimpleMIConfig(this);
            } catch (Exception e) {  // Catch errors loading the config file and exit out if found.
                MILog.severe(this.getMessager().getMessage(MultiverseMessage.ERROR_CONFIG_LOAD));
                MILog.severe(e.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return null;
            }
        }
        return this.config;
    }

    /**
     * @return the MIData object which contains data for this plugin.
     */
    public MIData getData() {
        if (this.data == null) {
            // Loads the data
            try {
                this.data = new FlatfileMIData(this);
            } catch (IOException e) {  // Catch errors loading the language file and exit out if found.
                MILog.severe(this.getMessager().getMessage(MultiverseMessage.ERROR_DATA_LOAD));
                MILog.severe(e.getMessage());
                Bukkit.getPluginManager().disablePlugin(this);
                return null;
            }
        }
        return this.data;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Messager getMessager() {
        return messager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setMessager(Messager messager) {
        if (messager == null)
            throw new IllegalArgumentException("The new messager can't be null!");

        this.messager = messager;
    }

    /**
     * @return The required protocol version of core.
     */
    public int getRequiredProtocol() {
        return this.requiresProtocol;
    }

    /**
     * @return The World Group manager for this plugin.
     */
    public WorldGroupManager getGroupManager() {
        return this.worldGroupManager;
    }

    /**
     * @return The Profile manager for this plugin.
     */
    public ProfileManager getProfileManager() {
        return this.profileManager;
    }

    /**
     * @return A set of default shares (all false)
     */
    /*
    public Shares getDefaultShares() {
        return this.defaultShares;
    }
    */

    /**
     * @return A set of bypass shares (all true)
     */
    public Shares getBypassShares() {
        return this.bypassShares;
    }

    /**
     * Handles the sharing for a player going from world fromWorld to world toWorld with the specified shares object.
     * This will handle all the switching/saving of inventories/health/exp/hunger/effects.
     * This method assumes all other checks have been made and WILL process the transfer.
     *
     * @param player    Player who will be handled.
     * @param fromWorld The world they are coming from.
     * @param toWorld   The world they are going to.
     * @param shares    The set of shares to affect the transfer.
     */
    public void handleSharing(Player player, World fromWorld, World toWorld, Shares shares) {
        WorldProfile fromWorldProfile = this.getProfileManager().getWorldProfile(fromWorld.getName());
        PlayerProfile fromWorldPlayerProfile = fromWorldProfile.getPlayerData(player);
        WorldProfile toWorldProfile = this.getProfileManager().getWorldProfile(toWorld.getName());
        PlayerProfile toWorldPlayerProfile = toWorldProfile.getPlayerData(player);

        // persist current stats for previous world if not sharing
        // then load any saved data
        if (!shares.isSharingInventory()) {
            fromWorldPlayerProfile.setInventoryContents(player.getInventory().getContents());
            fromWorldPlayerProfile.setArmorContents(player.getInventory().getArmorContents());
            player.getInventory().clear();
            player.getInventory().setContents(toWorldPlayerProfile.getInventoryContents());
            player.getInventory().setArmorContents(toWorldPlayerProfile.getArmorContents());
        }
        if (!shares.isSharingHealth()) {
            fromWorldPlayerProfile.setHealth(player.getHealth());
            player.setHealth(toWorldPlayerProfile.getHealth());
        }
        if (!shares.isSharingHunger()) {
            fromWorldPlayerProfile.setFoodLevel(player.getFoodLevel());
            fromWorldPlayerProfile.setExhaustion(player.getExhaustion());
            fromWorldPlayerProfile.setSaturation(player.getSaturation());
            player.setFoodLevel(toWorldPlayerProfile.getFoodLevel());
            player.setExhaustion(toWorldPlayerProfile.getExhaustion());
            player.setSaturation(toWorldPlayerProfile.getSaturation());
        }
        if (!shares.isSharingExp()) {
            fromWorldPlayerProfile.setExp(player.getExp());
            fromWorldPlayerProfile.setLevel(player.getLevel());
            fromWorldPlayerProfile.setTotalExperience(player.getTotalExperience());
            player.setExp(toWorldPlayerProfile.getExp());
            player.setLevel(toWorldPlayerProfile.getLevel());
            player.setTotalExperience(toWorldPlayerProfile.getTotalExperience());
        }
        /*
        if (!shares.isSharingEffects()) {
            // Where is the effects API??
        }
        */

        this.getData().updatePlayerData(fromWorld.getName(), fromWorldPlayerProfile);
        this.getData().updatePlayerData(toWorld.getName(), toWorldPlayerProfile);
    }
}