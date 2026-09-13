package com.reazip.economycraft.shop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manual shop-grid order: best / most useful first within each category. */
final class ShopOrder {
    private static final Map<String, Integer> INDEX = build();

    private ShopOrder() {}

    static int index(String path) {
        if (path == null) return Integer.MAX_VALUE;
        Integer value = INDEX.get(path);
        return value != null ? value : Integer.MAX_VALUE;
    }

    private static Map<String, Integer> build() {
        List<String> order = new ArrayList<>();
        addAll(order, "netherite_pickaxe", "netherite_shovel", "netherite_axe", "netherite_hoe", "diamond_pickaxe", "diamond_shovel", "diamond_axe", "diamond_hoe", "iron_pickaxe", "iron_shovel", "iron_axe", "iron_hoe");
        addAll(order, "golden_pickaxe", "golden_shovel", "golden_axe", "golden_hoe", "copper_pickaxe", "copper_shovel", "copper_axe", "copper_hoe", "stone_pickaxe", "stone_shovel", "stone_axe", "stone_hoe");
        addAll(order, "wooden_pickaxe", "wooden_shovel", "wooden_axe", "wooden_hoe", "shears", "brush", "fishing_rod", "carrot_on_a_stick", "warped_fungus_on_a_stick", "netherite_sword", "netherite_spear", "diamond_sword");
        addAll(order, "diamond_spear", "iron_sword", "iron_spear", "golden_sword", "golden_spear", "copper_sword", "copper_spear", "stone_sword", "stone_spear", "wooden_sword", "wooden_spear", "bow");
        addAll(order, "crossbow", "arrow", "spectral_arrow", "snowball", "netherite_helmet", "netherite_chestplate", "netherite_leggings", "netherite_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots");
        addAll(order, "golden_helmet", "golden_chestplate", "golden_leggings", "golden_boots", "iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "copper_helmet", "copper_chestplate", "copper_leggings", "copper_boots");
        addAll(order, "chainmail_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots", "turtle_helmet", "wolf_armor", "netherite_horse_armor", "diamond_horse_armor");
        addAll(order, "golden_horse_armor", "iron_horse_armor", "copper_horse_armor", "leather_horse_armor", "netherite_nautilus_armor", "diamond_nautilus_armor", "golden_nautilus_armor", "iron_nautilus_armor", "copper_nautilus_armor", "shield", "armor_stand");
        addAll(order, "sentry_armor_trim_smithing_template", "vex_armor_trim_smithing_template", "wild_armor_trim_smithing_template", "coast_armor_trim_smithing_template", "dune_armor_trim_smithing_template", "wayfinder_armor_trim_smithing_template", "raiser_armor_trim_smithing_template", "shaper_armor_trim_smithing_template", "host_armor_trim_smithing_template", "ward_armor_trim_smithing_template", "silence_armor_trim_smithing_template", "tide_armor_trim_smithing_template");
        addAll(order, "snout_armor_trim_smithing_template", "rib_armor_trim_smithing_template", "eye_armor_trim_smithing_template", "spire_armor_trim_smithing_template", "flow_armor_trim_smithing_template", "bolt_armor_trim_smithing_template", "netherite_upgrade_smithing_template", "cooked_beef");
        addAll(order, "beef", "cooked_porkchop", "porkchop", "cooked_chicken", "chicken", "cooked_mutton", "mutton", "cooked_rabbit", "rabbit", "cooked_salmon", "salmon", "cooked_cod");
        addAll(order, "cod", "golden_apple", "enchanted_golden_apple", "apple", "golden_carrot", "bread", "mushroom_stew", "rabbit_stew");
        addAll(order, "cookie", "sweet_berries", "glow_berries", "chorus_fruit", "popped_chorus_fruit", "tropical_fish", "pufferfish", "dried_kelp", "sugar");
        addAll(order, "wheat", "hay_block", "wheat_seeds", "potato", "baked_potato", "poisonous_potato", "carrot", "beetroot", "beetroot_soup", "beetroot_seeds", "melon_slice", "glistering_melon_slice");
        addAll(order, "melon", "melon_seeds", "pumpkin", "carved_pumpkin", "pumpkin_pie", "pumpkin_seeds", "cocoa_beans", "cactus", "cactus_flower", "sugar_cane", "nether_wart");
        addAll(order, "sniffer_egg", "turtle_egg", "egg", "blue_egg", "brown_egg", "dragon_egg", "milk_bucket", "slime_ball", "slime_block", "blaze_rod", "blaze_powder", "bone");
        addAll(order, "bone_meal", "bone_block", "wither_skeleton_skull", "dragon_head", "creeper_head", "piglin_head", "skeleton_skull", "zombie_head", "leather", "rabbit_hide", "rabbit_foot", "feather");
        addAll(order, "string", "honey_bottle", "honeycomb", "honey_block", "honeycomb_block", "bee_nest", "turtle_scute", "armadillo_scute", "ink_sac", "glow_ink_sac", "spider_eye", "fermented_spider_eye");
        addAll(order, "gunpowder", "rotten_flesh", "phantom_membrane", "magma_cream", "ghast_tear", "breeze_rod", "goat_horn", "lead", "axolotl_bucket", "tadpole_bucket", "salmon_bucket", "cod_bucket");
        addAll(order, "tropical_fish_bucket", "pufferfish_bucket", "cake", "netherite_block", "netherite_ingot", "netherite_scrap", "diamond_block", "diamond", "emerald_block");
        addAll(order, "emerald", "gold_block", "gold_ingot", "gold_nugget", "raw_gold", "iron_block", "iron_ingot", "iron_nugget", "raw_iron", "copper_ingot", "copper_nugget", "raw_copper");
        addAll(order, "lapis_block", "lapis_lazuli", "redstone_block", "redstone", "coal_block", "coal", "amethyst_block", "amethyst_shard", "nether_star", "totem_of_undying", "ender_pearl", "wind_charge");
        addAll(order, "nautilus_shell", "wither_rose", "piston", "sticky_piston", "observer", "dispenser", "dropper", "hopper", "repeater", "comparator", "lever", "oak_button");
        addAll(order, "stone_button", "oak_pressure_plate", "stone_pressure_plate", "light_weighted_pressure_plate", "heavy_weighted_pressure_plate", "tripwire_hook", "daylight_detector", "target", "note_block", "trapped_chest", "oak_door", "iron_door");
        addAll(order, "oak_trapdoor", "iron_trapdoor", "saddle", "rail", "powered_rail", "detector_rail", "activator_rail", "dried_ghast", "minecart", "chest_minecart", "hopper_minecart", "furnace_minecart");
        addAll(order, "oak_boat", "oak_chest_boat", "spruce_boat", "birch_boat", "jungle_boat", "acacia_boat", "dark_oak_boat", "mangrove_boat", "cherry_boat", "pale_oak_boat", "bamboo_raft", "white_harness");
        addAll(order, "light_gray_harness", "gray_harness", "black_harness", "brown_harness", "red_harness", "orange_harness", "yellow_harness", "lime_harness", "green_harness", "cyan_harness", "light_blue_harness", "blue_harness");
        addAll(order, "purple_harness", "magenta_harness", "pink_harness", "white_dye", "light_gray_dye", "gray_dye", "black_dye", "brown_dye", "red_dye", "orange_dye", "yellow_dye", "lime_dye");
        addAll(order, "green_dye", "cyan_dye", "light_blue_dye", "blue_dye", "purple_dye", "magenta_dye", "pink_dye", "white_bed", "light_gray_bed", "gray_bed", "black_bed", "brown_bed");
        addAll(order, "red_bed", "orange_bed", "yellow_bed", "lime_bed", "green_bed", "cyan_bed", "light_blue_bed", "blue_bed", "purple_bed", "magenta_bed", "pink_bed", "white_wool");
        addAll(order, "glass", "glass_pane", "blue_ice", "packed_ice", "ice", "snow_block", "snow", "music_disc_pigstep", "music_disc_otherside", "music_disc_relic", "music_disc_creator", "music_disc_creator_music_box");
        addAll(order, "music_disc_precipice", "music_disc_tears", "music_disc_lava_chicken", "music_disc_5", "disc_fragment_5", "music_disc_13", "music_disc_cat", "music_disc_blocks", "music_disc_chirp", "music_disc_far", "music_disc_mall", "music_disc_mellohi");
        addAll(order, "music_disc_stal", "music_disc_strad", "music_disc_ward", "music_disc_11", "music_disc_wait", "music_disc_bounce", "water_bottle", "splash_water_bottle", "lingering_water_bottle", "potion_of_healing_2", "potion_of_healing_1", "potion_of_regeneration_2", "potion_of_regeneration_extended", "potion_of_regeneration_1", "potion_of_strength_2", "potion_of_strength_extended");
        addAll(order, "potion_of_strength_1", "potion_of_fire_resistance_extended", "potion_of_fire_resistance_1", "potion_of_swiftness_2", "potion_of_swiftness_extended", "potion_of_swiftness_1", "potion_of_night_vision_extended", "potion_of_night_vision_1", "potion_of_invisibility_extended", "potion_of_invisibility_1", "potion_of_water_breathing_extended", "potion_of_water_breathing_1");
        addAll(order, "potion_of_leaping_2", "potion_of_leaping_extended", "potion_of_leaping_1", "potion_of_slow_falling_extended", "potion_of_slow_falling_1", "potion_of_harming_2", "potion_of_harming_1", "potion_of_poison_2", "potion_of_poison_extended", "potion_of_poison_1", "potion_of_slowness_2", "potion_of_slowness_extended", "potion_of_slowness_1");
        addAll(order, "potion_of_weakness_extended", "potion_of_weakness", "potion_of_oozing_1", "potion_of_weaving_1", "potion_of_wind_charged_1", "enchanted_book_mending_1", "enchanted_book_unbreaking_3", "enchanted_book_sharpness_5", "enchanted_book_smite_5", "enchanted_book_bane_of_arthropods_5", "enchanted_book_looting_3", "enchanted_book_sweeping_edge_3");
        addAll(order, "enchanted_book_fire_aspect_2", "enchanted_book_knockback_2", "enchanted_book_efficiency_5", "enchanted_book_fortune_3", "enchanted_book_silk_touch_1", "enchanted_book_power_5", "enchanted_book_punch_2", "enchanted_book_flame_1", "enchanted_book_infinity_1", "enchanted_book_piercing_4", "enchanted_book_multishot_1", "enchanted_book_quick_charge_3");
        addAll(order, "enchanted_book_loyalty_3", "enchanted_book_impaling_5", "enchanted_book_riptide_3", "enchanted_book_channeling_1", "enchanted_book_density_5", "enchanted_book_protection_4", "enchanted_book_feather_falling_4", "enchanted_book_blast_protection_4", "enchanted_book_projectile_protection_4", "enchanted_book_fire_protection_4", "enchanted_book_thorns_3", "enchanted_book_respiration_3");
        addAll(order, "enchanted_book_aqua_affinity_1", "enchanted_book_depth_strider_3", "enchanted_book_frost_walker_2", "enchanted_book_soul_speed_3", "enchanted_book_swift_sneak_3", "enchanted_book_luck_of_the_sea_3", "enchanted_book_lure_3", "enchanted_book_curse_of_binding_1", "enchanted_book_curse_of_vanishing_1");
        addAll(order, "bamboo", "torchflower_seeds", "torchflower", "pitcher_pod", "pitcher_plant", "oak_sapling", "spruce_sapling");
        addAll(order, "birch_sapling", "jungle_sapling", "acacia_sapling", "dark_oak_sapling", "mangrove_propagule", "cherry_sapling", "pale_oak_sapling", "azalea", "flowering_azalea", "oak_leaves", "spruce_leaves", "birch_leaves");
        addAll(order, "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "cherry_leaves", "pale_oak_leaves", "azalea_leaves", "flowering_azalea_leaves", "brown_mushroom");
        addAll(order, "red_mushroom", "brown_mushroom_block", "red_mushroom_block", "mushroom_stem", "dandelion", "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip");
        addAll(order, "pink_tulip", "oxeye_daisy", "cornflower", "lily_of_the_valley", "open_eyeblossom", "golden_dandelion", "sunflower", "lilac", "rose_bush", "peony", "pink_petals", "wildflowers");
        addAll(order, "short_grass", "fern", "large_fern", "dead_bush", "vine", "lily_pad", "moss_block", "moss_carpet", "pale_moss_block", "pale_moss_carpet", "pale_hanging_moss", "glow_lichen");
        addAll(order, "hanging_roots", "big_dripleaf", "small_dripleaf", "spore_blossom", "bush", "firefly_bush", "leaf_litter", "short_dry_grass", "tall_dry_grass", "mangrove_roots", "muddy_mangrove_roots", "twisting_vines");
        addAll(order, "weeping_vines", "flower_pot", "wet_sponge", "sponge", "sea_pickle", "kelp", "seagrass", "prismarine", "prismarine_bricks", "dark_prismarine", "prismarine_slab", "prismarine_stairs");
        addAll(order, "prismarine_wall", "prismarine_brick_slab", "prismarine_brick_stairs", "dark_prismarine_slab", "dark_prismarine_stairs", "prismarine_shard", "prismarine_crystals", "tube_coral_block", "tube_coral", "tube_coral_fan", "brain_coral_block", "brain_coral");
        addAll(order, "brain_coral_fan", "bubble_coral_block", "bubble_coral", "bubble_coral_fan", "fire_coral_block", "fire_coral", "fire_coral_fan", "horn_coral_block", "horn_coral", "horn_coral_fan", "dead_tube_coral_block", "dead_tube_coral");
        addAll(order, "dead_tube_coral_fan", "dead_brain_coral_block", "dead_brain_coral", "dead_brain_coral_fan", "dead_bubble_coral_block", "dead_bubble_coral", "dead_bubble_coral_fan", "dead_fire_coral_block", "dead_fire_coral", "dead_fire_coral_fan", "dead_horn_coral_block", "dead_horn_coral");
        addAll(order, "dead_horn_coral_fan", "ender_eye", "experience_bottle", "crafting_table", "furnace", "blast_furnace", "smoker", "smithing_table", "anvil", "enchanting_table", "grindstone", "stonecutter");
        addAll(order, "cartography_table", "loom", "fletching_table", "composter", "brewing_stand", "cauldron", "barrel", "lectern", "chest", "ender_chest", "shulker_box", "crafter");
        addAll(order, "jukebox", "lodestone", "respawn_anchor", "bucket", "water_bucket", "lava_bucket", "powder_snow_bucket", "sulfur_cube_bucket", "flint_and_steel", "flint", "fire_charge", "tnt");
        addAll(order, "firework_rocket", "compass", "recovery_compass", "echo_shard", "clock", "map", "spyglass", "name_tag", "item_frame", "glow_item_frame", "painting", "book");
        addAll(order, "writable_book", "paper", "bookshelf", "chiseled_bookshelf", "ladder", "scaffolding", "bundle", "bowl");
        addAll(order, "glass_bottle", "charcoal", "cobweb", "lightning_rod", "bell", "beehive", "tinted_glass");
        addAll(order, "stick", "oak_fence", "spruce_fence", "birch_fence", "jungle_fence", "acacia_fence", "dark_oak_fence", "mangrove_fence", "cherry_fence", "pale_oak_fence", "bamboo_fence", "crimson_fence");
        addAll(order, "warped_fence", "oak_fence_gate", "spruce_fence_gate", "birch_fence_gate", "jungle_fence_gate", "acacia_fence_gate", "dark_oak_fence_gate", "mangrove_fence_gate", "cherry_fence_gate", "pale_oak_fence_gate", "bamboo_fence_gate", "crimson_fence_gate");
        addAll(order, "warped_fence_gate", "oak_sign", "spruce_sign", "birch_sign", "jungle_sign", "acacia_sign", "dark_oak_sign", "mangrove_sign", "cherry_sign", "pale_oak_sign", "bamboo_sign", "crimson_sign");
        addAll(order, "warped_sign", "oak_hanging_sign", "spruce_hanging_sign", "birch_hanging_sign", "jungle_hanging_sign", "acacia_hanging_sign", "dark_oak_hanging_sign", "mangrove_hanging_sign", "cherry_hanging_sign", "pale_oak_hanging_sign", "bamboo_hanging_sign", "crimson_hanging_sign");
        addAll(order, "warped_hanging_sign", "oak_log", "oak_wood", "oak_planks", "oak_stairs");
        addAll(order, "oak_slab", "spruce_log", "spruce_wood", "spruce_planks", "spruce_stairs", "spruce_slab", "birch_log", "birch_wood", "birch_planks", "birch_stairs", "birch_slab", "jungle_log");
        addAll(order, "jungle_wood", "jungle_planks", "jungle_stairs", "jungle_slab", "acacia_log", "acacia_wood", "acacia_planks", "acacia_stairs", "acacia_slab", "dark_oak_log", "dark_oak_wood", "dark_oak_planks");
        addAll(order, "dark_oak_stairs", "dark_oak_slab", "mangrove_log", "mangrove_wood", "mangrove_planks", "mangrove_stairs", "mangrove_slab", "cherry_log", "cherry_wood", "cherry_planks", "cherry_stairs", "cherry_slab");
        addAll(order, "pale_oak_log", "pale_oak_wood", "pale_oak_planks", "pale_oak_stairs", "pale_oak_slab", "bamboo_block", "bamboo_planks", "bamboo_mosaic", "bamboo_stairs", "bamboo_slab", "bamboo_mosaic_stairs", "bamboo_mosaic_slab");
        addAll(order, "crimson_stem", "crimson_hyphae", "crimson_planks", "crimson_stairs", "crimson_slab", "warped_stem", "warped_hyphae", "warped_planks", "warped_stairs", "warped_slab", "grass_block", "dirt");
        addAll(order, "coarse_dirt", "rooted_dirt", "podzol", "mycelium", "mud", "packed_mud", "mud_bricks", "mud_brick_stairs", "mud_brick_slab", "mud_brick_wall", "gravel", "cobblestone");
        addAll(order, "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall", "mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall", "stone", "stone_stairs", "stone_slab", "smooth_stone", "smooth_stone_slab");
        addAll(order, "stone_bricks", "chiseled_stone_bricks", "cracked_stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall", "mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall", "andesite", "andesite_stairs");
        addAll(order, "andesite_slab", "andesite_wall", "polished_andesite", "polished_andesite_stairs", "polished_andesite_slab", "diorite", "diorite_stairs", "diorite_slab", "diorite_wall", "polished_diorite", "polished_diorite_stairs", "polished_diorite_slab");
        addAll(order, "granite", "granite_stairs", "granite_slab", "granite_wall", "polished_granite", "polished_granite_stairs", "polished_granite_slab", "deepslate", "cobbled_deepslate", "cobbled_deepslate_stairs", "cobbled_deepslate_slab", "cobbled_deepslate_wall");
        addAll(order, "polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall", "deepslate_bricks", "cracked_deepslate_bricks", "chiseled_deepslate", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall", "deepslate_tiles", "cracked_deepslate_tiles");
        addAll(order, "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall", "tuff", "tuff_stairs", "tuff_slab", "tuff_wall", "chiseled_tuff", "polished_tuff", "polished_tuff_stairs", "polished_tuff_slab", "polished_tuff_wall");
        addAll(order, "tuff_bricks", "chiseled_tuff_bricks", "tuff_brick_stairs", "tuff_brick_slab", "tuff_brick_wall", "calcite", "dripstone_block", "clay", "clay_ball", "sulfur", "sulfur_stairs", "sulfur_slab");
        addAll(order, "sulfur_wall", "chiseled_sulfur", "sulfur_spike", "potent_sulfur", "polished_sulfur", "polished_sulfur_stairs", "polished_sulfur_slab", "polished_sulfur_wall", "sulfur_bricks", "sulfur_brick_stairs", "sulfur_brick_slab", "sulfur_brick_wall");
        addAll(order, "cinnabar", "cinnabar_stairs", "cinnabar_slab", "cinnabar_wall", "chiseled_cinnabar", "polished_cinnabar", "polished_cinnabar_stairs", "polished_cinnabar_slab", "polished_cinnabar_wall", "cinnabar_bricks", "cinnabar_brick_stairs", "cinnabar_brick_slab");
        addAll(order, "cinnabar_brick_wall", "sand", "sandstone", "chiseled_sandstone", "cut_sandstone", "smooth_sandstone", "sandstone_stairs", "sandstone_slab", "sandstone_wall", "cut_sandstone_slab", "smooth_sandstone_stairs", "smooth_sandstone_slab");
        addAll(order, "red_sand", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone", "smooth_red_sandstone", "red_sandstone_stairs", "red_sandstone_slab", "red_sandstone_wall", "cut_red_sandstone_slab", "smooth_red_sandstone_stairs", "smooth_red_sandstone_slab", "bricks");
        addAll(order, "brick_stairs", "brick_slab", "brick_wall", "brick", "nether_bricks", "nether_brick_stairs", "nether_brick_slab", "nether_brick_wall", "nether_brick_fence", "chiseled_nether_bricks", "cracked_nether_bricks", "nether_brick");
        addAll(order, "red_nether_bricks", "red_nether_brick_stairs", "red_nether_brick_slab", "red_nether_brick_wall", "resin_bricks", "chiseled_resin_bricks", "resin_brick_stairs", "resin_brick_slab", "resin_brick_wall", "copper_block", "cut_copper", "cut_copper_stairs");
        addAll(order, "cut_copper_slab", "chiseled_copper", "copper_grate", "copper_bars", "copper_chain", "copper_golem_statue", "exposed_copper", "exposed_cut_copper", "exposed_cut_copper_stairs", "exposed_cut_copper_slab", "exposed_chiseled_copper", "exposed_copper_grate");
        addAll(order, "exposed_copper_bars", "exposed_copper_chain", "exposed_copper_golem_statue", "weathered_copper", "weathered_cut_copper", "weathered_cut_copper_stairs", "weathered_cut_copper_slab", "weathered_chiseled_copper", "weathered_copper_grate", "weathered_copper_bars", "weathered_copper_chain", "weathered_copper_golem_statue");
        addAll(order, "oxidized_copper", "oxidized_cut_copper", "oxidized_cut_copper_stairs", "oxidized_cut_copper_slab", "oxidized_chiseled_copper", "oxidized_copper_grate", "oxidized_copper_bars", "oxidized_copper_chain", "oxidized_copper_golem_statue", "waxed_copper_block", "waxed_cut_copper", "waxed_cut_copper_stairs");
        addAll(order, "waxed_cut_copper_slab", "waxed_chiseled_copper", "waxed_copper_grate", "waxed_copper_bars", "waxed_copper_chain", "waxed_copper_golem_statue", "waxed_exposed_copper", "waxed_exposed_cut_copper", "waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper_slab", "waxed_exposed_chiseled_copper", "waxed_exposed_copper_grate");
        addAll(order, "waxed_exposed_copper_bars", "waxed_exposed_copper_chain", "waxed_exposed_copper_golem_statue", "waxed_weathered_copper", "waxed_weathered_cut_copper", "waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper_slab", "waxed_weathered_chiseled_copper", "waxed_weathered_copper_grate", "waxed_weathered_copper_bars", "waxed_weathered_copper_chain", "waxed_weathered_copper_golem_statue");
        addAll(order, "waxed_oxidized_copper", "waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab", "waxed_oxidized_chiseled_copper", "waxed_oxidized_copper_grate", "waxed_oxidized_copper_bars", "waxed_oxidized_copper_chain", "waxed_oxidized_copper_golem_statue", "torch", "soul_torch", "copper_torch");
        addAll(order, "redstone_torch", "lantern", "soul_lantern", "copper_lantern", "jack_o_lantern", "redstone_lamp", "glowstone", "glowstone_dust", "shroomlight", "sea_lantern", "campfire", "soul_campfire");
        addAll(order, "candle", "white_candle", "light_gray_candle", "gray_candle", "black_candle", "brown_candle", "red_candle", "orange_candle", "yellow_candle", "lime_candle", "green_candle", "cyan_candle");
        addAll(order, "light_blue_candle", "blue_candle", "purple_candle", "magenta_candle", "pink_candle", "ochre_froglight", "verdant_froglight", "pearlescent_froglight", "end_rod", "copper_bulb", "exposed_copper_bulb", "weathered_copper_bulb");
        addAll(order, "oxidized_copper_bulb", "waxed_copper_bulb", "waxed_exposed_copper_bulb", "waxed_weathered_copper_bulb", "waxed_oxidized_copper_bulb", "exposed_copper_lantern", "weathered_copper_lantern");
        addAll(order, "oxidized_copper_lantern", "waxed_copper_lantern", "waxed_exposed_copper_lantern", "waxed_weathered_copper_lantern", "waxed_oxidized_copper_lantern", "obsidian", "crying_obsidian", "netherrack", "nether_quartz_ore", "quartz", "quartz_block", "quartz_pillar");
        addAll(order, "quartz_bricks", "chiseled_quartz_block", "quartz_stairs", "quartz_slab", "smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", "nether_wart_block", "warped_wart_block", "crimson_nylium", "warped_nylium", "crimson_fungus");
        addAll(order, "warped_fungus", "crimson_roots", "warped_roots", "soul_sand", "soul_soil", "magma_block", "basalt", "polished_basalt", "smooth_basalt", "blackstone", "blackstone_stairs", "blackstone_slab");
        addAll(order, "blackstone_wall", "gilded_blackstone", "polished_blackstone", "chiseled_polished_blackstone", "polished_blackstone_stairs", "polished_blackstone_slab", "polished_blackstone_wall", "polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab", "polished_blackstone_brick_wall");
        addAll(order, "end_stone", "end_stone_bricks", "end_stone_brick_stairs", "end_stone_brick_slab", "end_stone_brick_wall", "purpur_block", "purpur_pillar", "purpur_stairs", "purpur_slab");
        Map<String, Integer> index = new HashMap<>(order.size() * 2);
        for (int i = 0; i < order.size(); i++) {
            index.putIfAbsent(order.get(i), i);
        }
        return Map.copyOf(index);
    }

    private static void addAll(List<String> order, String... names) {
        for (String name : names) order.add(name);
    }
}
