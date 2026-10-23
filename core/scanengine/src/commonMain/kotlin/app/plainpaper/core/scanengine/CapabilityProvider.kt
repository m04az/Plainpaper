package app.plainpaper.core.scanengine

import app.plainpaper.core.domain.model.DeviceCapability
import app.plainpaper.core.scanengine.model.CapabilityDetails

/** Reports which pipeline this device gets (research R10). */
public interface CapabilityProvider {
    public fun current(): DeviceCapability

    public fun details(): CapabilityDetails
}
