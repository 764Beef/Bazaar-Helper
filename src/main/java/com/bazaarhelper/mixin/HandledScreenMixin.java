package com.bazaarhelper.mixin;

import com.bazaarhelper.gui.BazaarOverlayManager;
import com.bazaarhelper.util.BazaarOrderParser;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        String title = BazaarOrderParser.stripFormatting(this.getTitle().getString());
        if (BazaarOrderParser.isBazaarOrderScreen(title)) {
            BazaarOverlayManager.clear();
        }
    }
}
