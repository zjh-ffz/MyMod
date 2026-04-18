package com.example.mymod;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.Minecraft;

@Mod(MyMod.MODID)
public class MyMod {
    public static final String MODID = "mymod";

    public MyMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ClientHandlers::registerClientEvents);
    }

    private void setup(final FMLCommonSetupEvent event) {
        PgpManager.initialize();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PgpCommand.register(event.getDispatcher(), false);
    }

    public static final class ClientHandlers {
        public static void registerClientEvents() {
            MinecraftForge.EVENT_BUS.addListener(ClientHandlers::onRegisterClientCommands);
        }

        public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
            PgpCommand.register(event.getDispatcher(), true);
        }
    }
}
