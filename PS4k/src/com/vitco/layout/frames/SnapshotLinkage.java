package com.vitco.layout.frames;

import com.jidesoft.docking.DockableFrame;
import com.vitco.manager.action.types.StateActionPrototype;
import com.vitco.util.misc.SaveResourceLoader;

import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * construct the time line frame
 */
public class SnapshotLinkage extends FrameLinkagePrototype {
    @Override
    public DockableFrame buildFrame(String key, Frame mainFrame) {
        // construct frame
        frame = new DockableFrame(key,
                new SaveResourceLoader("resource/img/icons/frames/snapshot.png").asIconImage()
        );
        updateTitle(); // update the title

        // ...

        // register action to hide/show this frame and get visible state
        actionManager.registerAction("snapshot_state-action_show", new StateActionPrototype() {
            @Override
            public boolean getStatus() {
                return frame.isVisible();
            }

            @Override
            public void action(ActionEvent e) {
                toggleVisible();
            }
        });

        return frame;
    }
}
