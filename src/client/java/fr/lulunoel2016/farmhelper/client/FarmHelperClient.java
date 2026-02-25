package fr.lulunoel2016.farmhelper.client;

import net.fabricmc.api.ClientModInitializer;

public class FarmHelperClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        TickHandler.register();
    }
}
