package dev.boarbot.interactives.boarmanage;

import com.google.gson.Gson;
import dev.boarbot.bot.config.StringConfig;
import dev.boarbot.bot.config.components.IndivComponentConfig;
import dev.boarbot.interactives.Interactive;
import dev.boarbot.util.data.types.GuildData;
import dev.boarbot.util.interactive.StopType;
import dev.boarbot.util.generators.EmbedGenerator;
import dev.boarbot.util.interactive.InteractiveUtil;
import lombok.extern.log4j.Log4j2;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Log4j2
public class SetupInteractive extends Interactive {
    private int page = 0;
    private final EmbedGenerator embedGen = new EmbedGenerator("");
    private final MessageEditBuilder editedMsg = new MessageEditBuilder();
    private ActionRow[] curComponents = new ActionRow[0];

    private List<GuildChannel> chosenChannels = new ArrayList<>();
    private boolean isSb = false;

    private final Map<String, IndivComponentConfig> COMPONENTS = this.config.getComponentConfig().getSetup();

    public SetupInteractive(SlashCommandInteractionEvent initEvent) {
        super(initEvent);
    }

    @Override
    public void execute(GenericComponentInteractionCreateEvent compEvent) {
        compEvent.deferEdit().queue();

        if (!this.initEvent.getUser().getId().equals(compEvent.getUser().getId())) {
            return;
        }

        String compID = compEvent.getComponentId().split(",")[1];

        try {
            switch (compID) {
                case "CHANNEL_SELECT" -> this.doChannels(compEvent);
                case "SB_YES", "SB_NO" -> this.doSb(compID);
                case "NEXT" -> this.doNext();
                case "INFO" -> this.doInfo();
                case "CANCEL" -> this.stop(StopType.CANCELLED);
            }
        } catch (Exception exception){
            log.error("Failed to create file from image data!", exception);
        }
    }

    public void doChannels(GenericComponentInteractionCreateEvent compEvent) {
        Button nextBtn = ((Button) this.curComponents[1].getComponents().get(2)).withDisabled(false);

        this.chosenChannels = ((EntitySelectInteractionEvent) compEvent).getMentions().getChannels();

        this.curComponents[1].getComponents().set(2, nextBtn);
        this.editedMsg.setComponents(this.curComponents);
        this.interaction.getHook().editOriginal(this.editedMsg.build()).complete();
    }

    public void doSb(String compID) throws IOException {
        StringConfig strConfig = this.config.getStringConfig();
        Map<String, String> colorConfig = this.config.getColorConfig();

        this.isSb = compID.equals("SB_YES");

        Button nextBtn = ((Button) this.curComponents[1].getComponents().get(2)).withDisabled(false);
        this.curComponents[1].getComponents().set(2, nextBtn);

        this.embedGen.setStr(strConfig.getSetupFinished2() + (this.isSb ? "%%green%%Yes" : "%%error%%No"));
        this.embedGen.setColor(colorConfig.get("font"));

        this.editedMsg.setFiles(this.embedGen.generate()).setComponents(this.curComponents);
        this.interaction.getHook().editOriginal(this.editedMsg.build()).complete();
    }

    public void doNext() throws IOException {
        StringConfig strConfig = this.config.getStringConfig();
        Map<String, String> colorConfig = this.config.getColorConfig();

        if (this.page == 0) {
            this.page = 1;

            this.updateCurComponents();

            Button nextBtn = ((Button) this.curComponents[1].getComponents().get(2)).asDisabled()
                .withStyle(ButtonStyle.SUCCESS)
                .withLabel("Finish");

            this.curComponents[1].getComponents().set(2, nextBtn);

            this.embedGen.setStr(strConfig.getSetupUnfinished2());
            this.embedGen.setColor(colorConfig.get("font"));

            this.editedMsg.setFiles(this.embedGen.generate()).setComponents(this.curComponents);
            this.interaction.getHook().editOriginal(this.editedMsg.build()).complete();

            return;
        }

        this.stop(StopType.FINISHED);
    }

    public void doInfo() throws IOException {
        StringConfig strConfig = this.config.getStringConfig();
        Map<String, String> colorConfig = this.config.getColorConfig();

        if (this.page == 0) {
            this.embedGen.setStr(strConfig.getSetupInfoResponse1());
        } else {
            this.embedGen.setStr(strConfig.getSetupInfoResponse2());
        }

        this.embedGen.setColor(colorConfig.get("font"));

        MessageCreateBuilder infoMsg = new MessageCreateBuilder();
        infoMsg.setFiles(this.embedGen.generate());
        this.interaction.getHook().sendMessage(infoMsg.build()).setEphemeral(true).complete();
    }

    @Override
    public void stop(StopType type) throws IOException {
        StringConfig strConfig = this.config.getStringConfig();
        Map<String, String> colorConfig = this.config.getColorConfig();

        Interactive interactive = this.removeInteractive();

        if (interactive == null) {
            return;
        }

        switch (type) {
            case StopType.CANCELLED -> {
                this.embedGen.setStr(strConfig.getSetupCancelled());
                this.embedGen.setColor(colorConfig.get("error"));
            }

            case StopType.EXPIRED -> {
                this.embedGen.setStr(strConfig.getSetupExpired());
                this.embedGen.setColor(colorConfig.get("error"));
            }

            case StopType.FINISHED -> {
                GuildData guildData = new GuildData();

                List<String> chosenChannelsStr = new ArrayList<>();

                for (GuildChannel channel : this.chosenChannels) {
                    chosenChannelsStr.add(channel.getId());
                }

                guildData.setChannels(chosenChannelsStr.toArray(new String[0]));
                guildData.setSB(this.isSb);

                String guildDataPath = this.config.getPathConfig().getDatabaseFolder() +
                    this.config.getPathConfig().getGuildDataFolder() + this.interaction.getGuild().getId() + ".json";
                Gson g = new Gson();
                BufferedWriter writer = new BufferedWriter(new FileWriter(guildDataPath));

                writer.write(g.toJson(guildData));
                writer.close();

                this.embedGen.setStr(strConfig.getSetupFinishedAll());
                this.embedGen.setColor(colorConfig.get("green"));
            }
        }

        this.editedMsg.setFiles(this.embedGen.generate()).setComponents();
        this.interaction.getHook().editOriginal(this.editedMsg.build()).complete();
    }

    public void updateCurComponents() {
        if (this.page == 0) {
            this.curComponents = getFirstComponents();
            return;
        }

        this.curComponents = getSecondComponents();
    }

    public ActionRow[] getCurComponents() {
        if (this.curComponents.length == 0) {
            updateCurComponents();
        }

        return this.curComponents;
    }

    private ActionRow[] getFirstComponents() {
        ActionRow navRow = this.getNavRow();

        List<ItemComponent> channelSelect1 = InteractiveUtil.makeComponents(
            this.interaction.getId(), this.COMPONENTS.get("channelSelect")
        );

        return new ActionRow[] {
            ActionRow.of(channelSelect1),
            navRow
        };
    }

    private ActionRow[] getSecondComponents() {
        ActionRow navRow = this.getNavRow();

        List<ItemComponent> sbChoice = InteractiveUtil.makeComponents(
            this.interaction.getId(), this.COMPONENTS.get("sbYesBtn"), this.COMPONENTS.get("sbNoBtn")
        );

        return new ActionRow[] {
            ActionRow.of(sbChoice),
            navRow
        };
    }

    private ActionRow getNavRow() {
        List<ItemComponent> components = InteractiveUtil.makeComponents(
            this.interaction.getId(),
            this.COMPONENTS.get("cancelBtn"),
            this.COMPONENTS.get("infoBtn"),
            this.COMPONENTS.get("nextBtn")
        );

        return ActionRow.of(components);
    }
}
