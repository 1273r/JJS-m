package com.example.jjsmod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = JJSMod.MODID)
public class Events {
    private static final ResourceLocation HEALTH_MOD = ResourceLocation.fromNamespaceAndPath(JJSMod.MODID, "jjs_health");

    // ---------------------------------------------------------------- commands
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("jjs")
                .then(Commands.literal("honored").executes(c -> choose(c.getSource(), Kits.HONORED)))
                .then(Commands.literal("vessel").executes(c -> choose(c.getSource(), Kits.VESSEL)))
                .then(Commands.literal("clear").executes(c -> choose(c.getSource(), "")))
                .then(Commands.literal("awaken").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    JState st = JState.peek(p);
                    if (st != null) st.awake = 100f;
                    return 1;
                }))
                .then(Commands.literal("resetcd").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    JState st = JState.peek(p);
                    if (st != null) {
                        st.cdEnd.clear();
                        st.dashReady = 0;
                    }
                    return 1;
                })));
    }

    private static int choose(CommandSourceStack src, String cls) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        JState.drop(p);
        p.getPersistentData().putString("jjs_class", cls);
        applyHealth(p, !cls.isEmpty());
        p.setHealth(p.getMaxHealth());
        if (cls.isEmpty()) {
            PacketDistributor.sendToPlayer(p, HudPayload.empty());
        }
        String name = cls.equals(Kits.HONORED) ? "Honored One" : cls.equals(Kits.VESSEL) ? "Vessel" : "";
        src.sendSuccess(() -> Component.literal(cls.isEmpty() ? "Moveset removed" : "Moveset: " + name), false);
        return 1;
    }

    /** JJS health is on a 100 scale, so class players get 100 max HP and the wiki damage numbers apply 1:1. */
    private static void applyHealth(ServerPlayer p, boolean on) {
        AttributeInstance a = p.getAttribute(Attributes.MAX_HEALTH);
        if (a == null) return;
        if (on) {
            a.addOrUpdateTransientModifier(new AttributeModifier(HEALTH_MOD, 80.0, AttributeModifier.Operation.ADD_VALUE));
        } else {
            a.removeModifier(HEALTH_MOD);
        }
    }

    // ---------------------------------------------------------------- lifecycle
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !sp.getPersistentData().getString("jjs_class").isEmpty()) {
            applyHealth(sp, true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) JState.drop(sp);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        e.getEntity().getPersistentData().putString("jjs_class", e.getOriginal().getPersistentData().getString("jjs_class"));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            JState.drop(sp);
            if (!sp.getPersistentData().getString("jjs_class").isEmpty()) {
                applyHealth(sp, true);
                sp.setHealth(sp.getMaxHealth());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        // JJS has no passive regeneration, and vanilla regen would undo the 100-HP balance.
        for (ServerLevel l : e.getServer().getAllLevels()) {
            l.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, e.getServer());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent e) {
        JState.clearAll();
        Tasks.clear();
    }

    // ---------------------------------------------------------------- per-tick
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        JState st = JState.peek(sp);
        if (st == null) return;
        long now = sp.level().getGameTime();

        sp.getFoodData().setFoodLevel(20); // no hunger in JJS
        sp.getFoodData().setSaturation(5f);

        if (st.blocking) {
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 3, 1, false, false));
        } else if (now >= st.guardBrokenUntil && st.guard < JState.MAX_GUARD) {
            st.guard = Math.min(JState.MAX_GUARD, st.guard + 0.3f);
        }
        if (st.awakened && now >= st.awakeEnd) Moves.endAwaken(sp, st);

        st.sync(sp);
    }

    // ---------------------------------------------------------------- damage
    @SubscribeEvent
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        JState st = JState.peek(sp);
        if (st == null) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypes.FALL)) { // no fall damage in JJS
            e.setCanceled(true);
            return;
        }
        long now = sp.level().getGameTime();
        Combat.Ctx c = Combat.ctx;
        Entity attacker = src.getEntity();

        // Cursed Strikes: bullet i-frames
        if (now < st.bulletUntil && src.getDirectEntity() instanceof Projectile) {
            e.setCanceled(true);
            return;
        }
        // Manji Kick counter window
        if (now < st.counterUntil && attacker instanceof LivingEntity la && (c == null || c.counterable)) {
            e.setCanceled(true);
            Moves.manjiCounter(sp, st, la);
            return;
        }
        // Block (F)
        if (st.blocking && now >= st.guardBrokenUntil && (c == null || c.mode != Combat.UNBLOCKABLE)
                && (attacker != null || c != null)) {
            boolean any = c != null && c.mode == Combat.BLOCK_360;
            boolean facing = attacker == null || any || Combat.facing(sp, attacker.position());
            if (facing) {
                float amt = e.getAmount();
                st.guard -= amt * 2f; // [est] block meter drain
                ServerLevel l = (ServerLevel) sp.level();
                Vec3 f = sp.position().add(0, 1.2, 0).add(Combat.flat(sp).scale(0.6));
                Fx.dust(l, f, 0.7f, 0.9f, 1f, 1.6f, 8, 0.3);
                Fx.sfx(l, f, SoundEvents.SHIELD_BLOCK, 1f, 1.2f);
                if (c != null && c.mode == Combat.SEMI) e.setAmount(amt * 0.5f);
                else e.setCanceled(true);
                if (st.guard <= 0f) { // guard break
                    st.guard = 0f;
                    st.blocking = false;
                    st.guardBrokenUntil = now + 60;
                    Moves.stun(sp, 40);
                    Fx.sfx(l, sp.position(), SoundEvents.SHIELD_BREAK, 1f, 1f);
                }
            }
        }
    }

    /** Awakening meter fills from damage dealt and taken; damage interrupts interruptible casts. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post e) {
        float dmg = e.getNewDamage();
        if (e.getEntity() instanceof ServerPlayer v) {
            JState vs = JState.peek(v);
            if (vs != null) {
                vs.addAwake(dmg * 0.3f);
                if (dmg >= 1f && vs.cast != null && vs.cast.interruptible && vs.cast.live(v)) {
                    Moves.cancelCast(vs);
                }
            }
        }
        if (e.getSource().getEntity() instanceof ServerPlayer a) {
            JState as = JState.peek(a);
            if (as != null) as.addAwake(dmg * 0.4f);
        }
    }

    // ---------------------------------------------------------------- no vanilla combat / mining for class players
    private static boolean hasClass(Entity p) {
        return p != null && !p.getPersistentData().getString("jjs_class").isEmpty();
    }

    @SubscribeEvent
    public static void onVanillaAttack(AttackEntityEvent e) {
        if (!e.getEntity().level().isClientSide && hasClass(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        boolean cls = e.getLevel().isClientSide ? ClientState.active : hasClass(e.getEntity());
        if (cls) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (hasClass(e.getPlayer())) e.setCanceled(true);
    }
}
