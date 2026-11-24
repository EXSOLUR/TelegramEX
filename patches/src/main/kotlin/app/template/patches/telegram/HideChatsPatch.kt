package app.template.patches.telegram

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS = "Lapp/template/extension/TelegramHideHelper;"

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

@Suppress("unused")
val hideChatsPatch = patch(
    name = "Hide Chats (TelegramEX)",
    description = "Sohbetleri ana listeden gizler. Arama çubuğuna #gizle, #cikar, #ac, #temizle yazarak kontrol edilir.",
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
                        invoke-static/range { v$reg .. v$reg }, ${EXTENSION_CLASS}->filterDialogs(Ljava/util/ArrayList;)Ljava/util/ArrayList;
                        move-result-object v$reg
                    """
                )
            }
        }

        ChatActivityResumeFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p0 }, ${EXTENSION_CLASS}->onChatOpened(Ljava/lang/Object;)V
            """
        )

        DialogsSearchFingerprint.method.addInstructions(
            0,
            """
                invoke-static { p1 }, ${EXTENSION_CLASS}->onSearchQuery(Ljava/lang/String;)V
            """
        )
    }
}
