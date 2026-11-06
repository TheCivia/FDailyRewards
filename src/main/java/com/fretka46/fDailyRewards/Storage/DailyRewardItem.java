package com.fretka46.fDailyRewards.Storage;

import org.bukkit.Material;

/** Neměnný popis položky v menu. Úpravy vždy vytvářejí kopii (cache v ConfigManageru se nikdy nemění). */
public record DailyRewardItem(Material material, int customModelData, String name, String lore, String tooltipStyle,
                              String itemModel) {

    public DailyRewardItem(Material material, int customModelData, String name, String lore, String tooltipStyle) {
        this(material, customModelData, name, lore, tooltipStyle, null);
    }

    public DailyRewardItem withName(String newName) {
        return new DailyRewardItem(material, customModelData, newName, lore, tooltipStyle, itemModel);
    }

    /** Vzhled (materiál + model) ze šablony, zbytek (název, lore, tooltip styl) zůstává z odměny. */
    public DailyRewardItem withLook(DailyRewardItem template) {
        return new DailyRewardItem(template.material, template.customModelData, name, lore, tooltipStyle, template.itemModel);
    }
}
