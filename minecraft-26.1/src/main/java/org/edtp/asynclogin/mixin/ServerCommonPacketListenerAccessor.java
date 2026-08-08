package org.edtp.asynclogin.mixin;

import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerCommonPacketListenerImpl.class)
public interface ServerCommonPacketListenerAccessor {
    @Accessor("server")
    MinecraftServer asynclogin$getServer();

    @Accessor("connection")
    Connection asynclogin$getConnection();

    @Invoker("createCookie")
    CommonListenerCookie asynclogin$createCookie(ClientInformation clientInformation);
}
