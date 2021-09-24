package fuzs.pickupnotifier.lib.config;

import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ConfigSync {
    public static final ConfigSync INSTANCE = new ConfigSync(ConfigTracker.INSTANCE);
    private final ConfigTracker tracker;

    private ConfigSync(final ConfigTracker tracker) {
        this.tracker = tracker;
    }

//    public List<Pair<String, FMLHandshakeMessages.S2CConfigData>> syncConfigs(boolean isLocal) {
//        final Map<String, byte[]> configData = tracker.configSets().get(ModConfig.Type.SERVER).stream().collect(Collectors.toMap(ModConfig::getFileName, mc -> {
//            try {
//                return Files.readAllBytes(mc.getFullPath());
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }));
//        return configData.entrySet().stream().map(e->Pair.of("Config "+e.getKey(), new FMLHandshakeMessages.S2CConfigData(e.getKey(), e.getValue()))).collect(Collectors.toList());
//    }
//
//    public void receiveSyncedConfig(final FMLHandshakeMessages.S2CConfigData s2CConfigData, final Supplier<NetworkEvent.Context> contextSupplier) {
//        if (!Minecraft.getInstance().isLocalServer()) {
//            Optional.ofNullable(tracker.fileMap().get(s2CConfigData.getFileName())).ifPresent(mc-> mc.acceptSyncedConfig(s2CConfigData.getBytes()));
//        }
//    }
}