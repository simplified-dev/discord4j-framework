package dev.simplified.discordapi.component.scope;

import dev.simplified.discordapi.component.Component;
import dev.simplified.discordapi.component.capability.ModalUpdatable;
import dev.simplified.discordapi.component.capability.UserInteractable;
import dev.simplified.discordapi.component.layout.Label;

/**
 * Placement scope for interactive components that can be wrapped in a {@link Label} layout.
 *
 * <p>
 * A label layout pairs a descriptive text label with an interactive component, providing
 * additional context for the user. Components implementing this interface declare that they
 * are valid targets for label wrapping, carry a {@link UserInteractable#getIdentifier()
 * custom_id} for interaction routing, and can update their state from modal submission data
 * via {@link ModalUpdatable#updateFromData}.
 *
 * @see Label
 * @see ModalUpdatable
 */
public interface LabelComponent extends Component, ModalUpdatable, UserInteractable {

}
