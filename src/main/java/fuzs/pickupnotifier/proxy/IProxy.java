package fuzs.pickupnotifier.proxy;

import fuzs.pickupnotifier.network.message.Message;
import net.minecraft.resources.ResourceLocation;

public interface IProxy {

    Object getClientInstance();

    Object getServerInstance();

    void registerClientReceiver(ResourceLocation channelName, Message message);

    void registerServerReceiver(ResourceLocation channelName, Message message);

}
