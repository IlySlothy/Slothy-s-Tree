// Discord enums we use. Kept in one place so the main worker file stays small
// and we don't pull a full library into the Worker bundle.

export const InteractionType = Object.freeze({
    PING: 1,
    APPLICATION_COMMAND: 2,
    MESSAGE_COMPONENT: 3,
    APPLICATION_COMMAND_AUTOCOMPLETE: 4,
    MODAL_SUBMIT: 5,
});

export const InteractionResponseType = Object.freeze({
    PONG: 1,
    CHANNEL_MESSAGE_WITH_SOURCE: 4,
    DEFERRED_CHANNEL_MESSAGE_WITH_SOURCE: 5,
    DEFERRED_UPDATE_MESSAGE: 6,
    UPDATE_MESSAGE: 7,
});

export const InteractionResponseFlags = Object.freeze({
    EPHEMERAL: 1 << 6,
});

export const MessageComponentType = Object.freeze({
    ACTION_ROW: 1,
    BUTTON: 2,
    STRING_SELECT: 3,
});

export const ButtonStyle = Object.freeze({
    PRIMARY: 1,
    SECONDARY: 2,
    SUCCESS: 3,
    DANGER: 4,
    LINK: 5,
});

export const ApplicationCommandOptionType = Object.freeze({
    SUB_COMMAND: 1,
    SUB_COMMAND_GROUP: 2,
    STRING: 3,
    INTEGER: 4,
    BOOLEAN: 5,
    USER: 6,
    CHANNEL: 7,
    ROLE: 8,
    MENTIONABLE: 9,
    NUMBER: 10,
    ATTACHMENT: 11,
});
