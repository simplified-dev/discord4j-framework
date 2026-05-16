package dev.simplified.discordapi.component.scope;

import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.TextDisplay;
import dev.simplified.discordapi.component.interaction.Modal;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.component.interaction.TextInput;
import dev.simplified.discordapi.component.layout.Label;

/**
 * Placement scope for components valid at the top level of a Discord modal.
 *
 * <p>
 * Only {@link Label Label} and
 * {@link TextDisplay TextDisplay} implement this interface.
 * Interactive components such as {@link TextInput
 * TextInput} and {@link SelectMenu SelectMenu} must
 * be wrapped in a {@code Label} to appear in a modal.
 *
 * @see Modal
 */
public interface TopLevelModalComponent extends Component {

}
