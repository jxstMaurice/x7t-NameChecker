package com.x7t.namechecker;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class X7tNameChecker implements ModInitializer {
    public static final String MOD_ID = "x7tnamechecker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("x7t Name Checker loaded!");
    }
}
