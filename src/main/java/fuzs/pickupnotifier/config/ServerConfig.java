package fuzs.pickupnotifier.config;

import fuzs.puzzleslib.config.AbstractConfig;
import fuzs.puzzleslib.config.annotation.Config;

public class ServerConfig extends AbstractConfig {
    @Config(description = {"Collect partial pick-up entries (when there isn't enough room in your inventory) in the log.", "Might accidentally log items that have not been picked up, therefore it can be disabled."})
    public boolean partialPickUps = true;

    public ServerConfig() {
        super("");
    }
}
