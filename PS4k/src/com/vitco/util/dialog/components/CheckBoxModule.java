package com.vitco.util.dialog.components;

import com.vitco.util.dialog.BlankDialogModule;

import javax.swing.*;
import java.awt.*;

/**
 * Module that has a checkbox
 */
public class CheckBoxModule extends BlankDialogModule {

    // the checkbox reference
    private final JCheckBox checkbox;

    // constructor
    public CheckBoxModule(String identifier, String caption) {
        super(identifier);
        setLayout(new BorderLayout());
        // add checkbox
        checkbox = new JCheckBox(caption);
        checkbox.setFocusable(false);
        // add to west so it doesn't stretch over the whole width
        add(checkbox, BorderLayout.WEST);
    }

    // get the value of this object
    @Override
    protected Object getValue(String identifier) {
        return checkbox.isSelected();
    }
}
