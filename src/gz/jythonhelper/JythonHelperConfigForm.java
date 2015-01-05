package gz.jythonhelper;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: gongze
 * Date: 12/3/13
 * Time: 3:33 PM
 */
public class JythonHelperConfigForm implements Configurable {
    public static final String JYTHON_HELPER = "Jython Helper";
    private JPanel mainPane;
    private JList<String> libList;
    private JButton addLibButton;
    private JButton removeLibButton;
    private Project project;

    public JythonHelperConfigForm(final Project project) {
        this.project = project;
        addLibButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FileChooserDescriptor fcd = FileChooserDescriptorFactory.createMultipleJavaPathDescriptor();
                FileChooser.chooseFiles(fcd, project, null, new Consumer<List<VirtualFile>>() {
                    @Override
                    public void consume(List<VirtualFile> virtualFiles) {
                        DefaultListModel<String> model = (DefaultListModel<String>) JythonHelperConfigForm.this.libList.getModel();
                        for (VirtualFile virtualFile : virtualFiles) {
                            model.addElement(virtualFile.getPresentableUrl());
                        }
                    }
                });
            }
        });
        removeLibButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int[] sis = libList.getSelectedIndices();
                if (sis.length > 0) {
                    DefaultListModel<String> model = (DefaultListModel<String>) JythonHelperConfigForm.this.libList.getModel();
                    for (int i = sis.length - 1; i >= 0; i--) {
                        model.remove(sis[i]);
                    }
                }
            }
        });
    }

    @Nls
    @Override
    public String getDisplayName() {
        return JYTHON_HELPER;
    }

    @Nullable
    @Override
    public String getHelpTopic() {
        return null;
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        return mainPane;
    }

    @Override
    public boolean isModified() {
        return true;
    }

    @Override
    public void apply() throws ConfigurationException {
        JythonHelperConfig.State myState = getState();
        ListModel model = this.libList.getModel();
        int size = model.getSize();
        String[] values = new String[size];
        for (int i = 0; i < values.length; i++) {
            values[i] = model.getElementAt(i).toString();
        }
        myState.javaLibs = values;
    }

    private JythonHelperConfig.State getState() {
        JythonHelperConfig config = JythonHelperConfig.getConfig(project);
        return config.getState();
    }

    @Override
    public void reset() {
        JythonHelperConfig.State myState = getState();
        DefaultListModel<String> model = new DefaultListModel<String>();
        String[] values = new String[0];
        if (myState.javaLibs != null) {
            values = myState.javaLibs;
        }
        for (String value : values) {
            model.addElement(value);
        }
        libList.setModel(model);
    }

    @Override
    public void disposeUIResources() {

    }
}
