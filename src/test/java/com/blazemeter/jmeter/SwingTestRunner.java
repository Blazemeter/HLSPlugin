package com.blazemeter.jmeter;

import static org.assertj.swing.junit.runner.Formatter.testNameFrom;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import org.assertj.core.util.Files;
import org.assertj.swing.junit.runner.FailureScreenshotTaker;
import org.junit.rules.MethodRule;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.mockito.junit.MockitoJUnit;
import org.mockito.quality.Strictness;

/**
 * Class which takes screenshot captures when a test fails and applies mockito rule (building mocks,
 * reset, etc).
 *
 * We use this to avoid having to use GuiTestRunner which requires GuiTest annotation and also would
 * require definition of Mockito rule for proper mocks initialization. Additionally, this class uses
 * existing failsafe reports directory to put failed-gui-tests which is cleaner than creating such
 * directory in root project directory (which is what GuiTestRunner does).
 */
public class SwingTestRunner extends BlockJUnit4ClassRunner {

    private static final FailureScreenshotTaker SCREENSHOT_TAKER = new FailureScreenshotTaker(
            buildGuiScreenshotsFolder());
    private static final MethodRule MOCKITO_RULE = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    private static File buildGuiScreenshotsFolder() {
        File ret = Paths.get("target", "failsafe-reports", "failed-gui-tests").toFile();
        Files.delete(ret);
        ret.mkdirs();
        return ret;
    }

    public SwingTestRunner(Class<?> klass) throws InitializationError {
        super(klass);
    }

    @Override
    protected Statement methodInvoker(FrameworkMethod method, Object test) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try {
                    method.invokeExplosively(test);
                } catch (Throwable t) {
                    takeScreenshot();
                    throw t;
                }
            }

            private void takeScreenshot() {
                SCREENSHOT_TAKER
                        .saveScreenshot(testNameFrom(method.getDeclaringClass(), method.getMethod()));
            }
        };
    }

    @Override
    protected List<MethodRule> rules(Object target) {
        List<MethodRule> ret = super.rules(target);
        ret.add(MOCKITO_RULE);
        return ret;
    }

}

