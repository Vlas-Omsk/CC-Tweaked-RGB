/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2022. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */
package dan200.computercraft.shared.computer.upload;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;

public enum UploadResult
{
    QUEUED,
    CONSUMED,
    ERROR;

    public static final Component FAILED_TITLE = new TranslatableComponent( "gui.computercraft.upload.failed" );
    public static final Component COMPUTER_OFF_MSG = new TranslatableComponent( "gui.computercraft.upload.failed.computer_off" );
    public static final Component TOO_MUCH_MSG = new TranslatableComponent( "gui.computercraft.upload.failed.too_much" );
}
