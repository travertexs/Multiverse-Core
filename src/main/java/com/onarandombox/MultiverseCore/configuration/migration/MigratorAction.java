package com.onarandombox.MultiverseCore.configuration.migration;

import org.bukkit.configuration.file.FileConfiguration;

/**
 * A migrator action is a single action that is performed when migrating a config.
 */
public interface MigratorAction {

    /**
     * Performs the migration action.
     *
     * @param config The target settings instance to migrate.
     */
    void migrate(FileConfiguration config);
}