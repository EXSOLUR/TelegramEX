package app.template.patches.telegram

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXT = "Lapp/template/extension/TelegramEXHelper;"

object GetDialogsArrayFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/ui/DialogsActivity;",
    name = "getDialogsArray",
    returnType = "Ljava/util/ArrayList;",
    parameters = listOf("I", "I", "I", "Z"),
)

object ChatActivityResumeFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/ui/ChatActivity;",
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

object DialogsSearchFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/ui/Adapters/DialogsSearchAdapter;",
    name = "searchDialogs",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;"),
)

object ContactsActivityResumeFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/ui/ContactsActivity;",
    name = "onResume",
    returnType = "V",
    parameters = listOf(),
)

object NoForwardsFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/messenger/MessagesController;",
    name = "isChatNoForwards",
    returnType = "Z",
    parameters = listOf("J"),
)

object SponsoredMessagesFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/messenger/MessagesController;",
    name = "getSponsoredMessages",
    parameters = listOf("J"),
)

object SendTypingFingerprint : Fingerprint(
    definingClass = "Lorg/telegram/messenger/MessagesController;",
    name = "sendTyping",
    returnType = "V",
)

@Suppress("unused")
val hideChatsPatch = patch(
    name = "Hide Chats (TelegramEX)",
    description = "Sohbetleri gizler. Arama çubuğu komutları: #gizle, #cikar, #ac, #temizle",
    default = true
) {
    extendWith("extensions/extension.mpe")

    execute {
        val getDialogsMethod = GetDialogsArrayFingerprint.method
        val instructions = getDialogsMethod.implementation!!.instructions
        for ((index, instruction) in instructions.withIndex().reversed()) {
            if (instruction.opcode == Opcode.RETURN_OBJECT) {
                val reg = (instruction as OneRegisterInstruction).registerA
                getDialogsMethod.addInstructions(
                    index,
                    """
                        invoke-static/range { v$reg .. v$reg }, $EXT->filterDialogs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                        move-result-object v$reg
                    """
                )
            }
        }

        ChatActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p0 }, $EXT->onChatOpened(Ljava/lang/Object;)V
            """
        )

        DialogsSearchFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p1 }, $EXT->onSearchQuery(Ljava/lang/String;)V
            """
        )
    }
}

@Suppress("unused")
val hideContactsPatch = patch(
    name = "Hide Contacts (TelegramEX)",
    description = "Kişileri rehberden gizler. Arama çubuğu komutları: #kisigizle, #kisicikar, #kisiac",
    default = true
) {
    extendWith("extensions/extension.mpe")

    execute {
        ContactsActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p0 }, $EXT->filterContacts(Ljava/lang/Object;)V
            """
        )
    }
}

@Suppress("unused")
val allowCopySavePatch = patch(
    name = "Allow Copy & Save (TelegramEX)",
    description = "Kısıtlı kanal ve gruplarda kopyalama, iletme ve ekran görüntüsü engelini kaldırır.",
    default = true
) {
    execute {
        NoForwardsFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """
        )
    }
}

@Suppress("unused")
val hideSponsoredPatch = patch(
    name = "Hide Sponsored Messages (TelegramEX)",
    description = "Kanallardaki sponsorlu reklam mesajlarını gizler.",
    default = true
) {
    execute {
        SponsoredMessagesFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """
        )
    }
}

@Suppress("unused")
val ghostModePatch = patch(
    name = "Ghost Mode - No Typing (TelegramEX)",
    description = "Karşı tarafa 'yazıyor...' bilgisini engeller. Arama çubuğundan #hayalet yazarak aç/kapa.",
    default = true
) {
    extendWith("extensions/extension.mpe")

    execute {
        SendTypingFingerprint.method.addInstructions(
            0,
            """
                invoke-static {}, $EXT->isGhostMode()Z
                move-result v0
                if-eqz v0, :not_ghost
                return-void
                :not_ghost
                nop
            """
        )
    }
}
