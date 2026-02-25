package fr.lulunoel2016.farmhelper.mixin.client;

import fr.lulunoel2016.farmhelper.client.RecapParser;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatRecapMixin {

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"))
    private void onChatMessage3(Text message, MessageSignatureData signatureData, MessageIndicator indicator, CallbackInfo ci) {
        if (message == null) return;
        RecapParser.onChatMessage(message.getString());
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onChatMessage1(Text message, CallbackInfo ci) {
        if (message == null) return;
        RecapParser.onChatMessage(message.getString());
    }
}
