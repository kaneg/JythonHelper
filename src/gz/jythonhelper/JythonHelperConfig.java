package gz.jythonhelper;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: gongze
 * Date: 12/3/13
 * Time: 2:24 PM
 */
@State(name = "JythonHelperConfig", storages = {@com.intellij.openapi.components.Storage(file = "$WORKSPACE_FILE$")})
public class JythonHelperConfig implements PersistentStateComponent<JythonHelperConfig.State> {
    public static class State {
        public String javaLibs[] = new String[0];
    }

    State myState = new State();

    @Nullable
    @Override
    public State getState() {
        return myState;
    }

    @Override
    public void loadState(State state) {
        myState = state;
    }

    public static JythonHelperConfig getConfig(Project project) {
        return ServiceManager.getService(project, JythonHelperConfig.class);
    }
}
