package gz.jythonhelper;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by IntelliJ IDEA.
 * User: gongze
 * Date: 12/3/13
 * Time: 10:37 AM
 */
public class JythonHelperAction extends AnAction {
    public void actionPerformed(AnActionEvent event) {
        final Project project = event.getData(PlatformDataKeys.PROJECT);
        final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        if (vFiles == null || vFiles.length == 0) {
            Messages.showWarningDialog("No file to be review", "Error");
            return;
        }
        VirtualFile target = vFiles[0];
        JythonHelperConfig config = JythonHelperConfig.getConfig(project);
        JythonHelperConfig.State state = config.getState();
        if (state == null || state.targetDirectory == null || state.targetDirectory.isEmpty()) {
            Messages.showInfoMessage("You need to set output directory and Java library first. Click OK to setting.", "Info");
            ShowSettingsUtil.getInstance().showSettingsDialog(project, JythonHelperConfigForm.JYTHON_HELPER);
            return;
        }
        String targetDirectory = state.targetDirectory;
        String[] javaLibs = state.javaLibs;
        generatePy(targetDirectory, javaLibs, target.getPath());
        Messages.showInfoMessage("Python files have been generated in directory:" + state.targetDirectory, "Info");
        LocalFileSystem.getInstance().refresh(false);
    }

    private void generatePy(String outputDir, String[] libs, String... files) {
        EasyJython ej = new EasyJython(outputDir);
        ej.setLibs(libs);
        ej.generatePys(files);
    }

    @Override
    public void update(AnActionEvent event) {
        final VirtualFile[] vFiles = event.getData(PlatformDataKeys.VIRTUAL_FILE_ARRAY);
        setActionEnable(event, vFiles != null && 1 == vFiles.length &&
                (vFiles[0].isDirectory() || "py".equalsIgnoreCase(vFiles[0].getExtension())));
    }

    private void setActionEnable(AnActionEvent event, boolean isEnable) {
        event.getPresentation().setEnabled(isEnable);
    }
}
