package dev.boarbot.modals;

import dev.boarbot.BoarBotApp;
import dev.boarbot.api.util.Configured;
import dev.boarbot.interactives.ModalInteractive;
import dev.boarbot.util.modal.ModalUtil;
import dev.boarbot.util.time.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.GenericComponentInteractionCreateEvent;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class ModalHandler implements Configured {
    private final ModalInteractive receiver;

    private final ComponentInteraction interaction;
    private final User user;

    public ModalHandler(GenericComponentInteractionCreateEvent compEvent, ModalInteractive receiver) {
        this.interaction = compEvent.getInteraction();
        this.user = compEvent.getUser();

        this.receiver = receiver;

        String duplicateModalKey = ModalUtil.findDuplicateModalHandler(this.user.getId());

        if (duplicateModalKey != null) {
            try {
                BoarBotApp.getBot().getModalHandlers().get(duplicateModalKey).stop();
            } catch (Exception exception) {
                log.error("Something went wrong when terminating modal handler!", exception);
                return;
            }
        }

        BoarBotApp.getBot().getModalHandlers().put(this.interaction.getId() + this.user.getId(), this);
        CompletableFuture.runAsync(() -> this.delayStop(NUMS.getInteractiveIdle()));
    }

    public void execute(ModalInteractionEvent modalEvent) {
        this.receiver.attemptExecute(null, modalEvent, TimeUtil.getCurMilli());
        this.stop();
    }

    private void delayStop(long waitTime) {
        try {
            Thread.sleep(waitTime);
        } catch (InterruptedException exception) {
            this.stop();
        }

        this.stop();
    }

    public void stop() {
        BoarBotApp.getBot().getModalHandlers().remove(this.interaction.getId() + this.user.getId());
    }
}
