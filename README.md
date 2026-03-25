# Bazaar Helper

Bazaar Helper is a Fabric mod for Hypixel SkyBlock that keeps your Bazaar activity visible, automates tedious sell decisions, and keeps you aligned with the live market. Instead of pulling in the entire Gradle toolchain, just grab the release jar and drop it in your `mods/` folder.

## What you get

- **Order tracking** that remembers what you listed and how much you still have to deliver.
- **In-screen overlays** that show buy/sell price bands, profit estimates, and your current stack on the same HUD you already use.
- **Launch automation** that handles repetitive sell clicks once the market swings in your favor.
- **Lightweight install**: no extra configuration, no remapping, and no need to maintain a build environment.

## Download the release

1. Go to the [Bazaar Helper Releases](https://github.com/764Beef/Bazaar-Helper/releases) page.
2. Download the latest `bazaar-helper-*.jar` that matches your Minecraft version.
3. Copy the jar into your Fabric `mods/` directory and start Minecraft with Fabric loader—the mod is ready immediately.

## Requirements

> **Fabric Loader** · 0.16.5 (Minecraft 1.21.11)  
> **Java** · 21+  
> **Fabric API** · 0.102.0+1.21.1  
> **Hypixel SkyBlock** · active account (Bazaar Helper reads Hypixel’s Bazaar data live)  

Each requirement ties directly to the remapped release jar and Fabric loader version so you can install the artifact without mismatched dependencies.

## Why the jar?

The release jar is carefully remapped and packaged so it works with the shipped dependencies and the Fabric loader. Building locally means recreating that process yourself, which is time-consuming and invites version drift. Stick with the release artifact to avoid those pitfalls and focus on putting Bazaar data to work in-game.
