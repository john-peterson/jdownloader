package org.jdownloader.captcha.v2.solver.gui;

import jd.controlling.captcha.CaptchaSettings;
import jd.controlling.captcha.ClickCaptchaDialogHandler;
import jd.controlling.captcha.SkipException;
import jd.controlling.captcha.SkipRequest;
import jd.gui.swing.jdgui.JDGui;

import org.appwork.storage.config.JsonConfig;
import org.jdownloader.captcha.v2.Challenge;
import org.jdownloader.captcha.v2.ChallengeSolver;
import org.jdownloader.captcha.v2.challenge.clickcaptcha.ClickCaptchaChallenge;
import org.jdownloader.captcha.v2.challenge.clickcaptcha.ClickedPoint;
import org.jdownloader.captcha.v2.challenge.stringcaptcha.ClickCaptchaResponse;
import org.jdownloader.captcha.v2.solver.Captcha9kwSettings;
import org.jdownloader.captcha.v2.solver.Captcha9kwSolverClick;
import org.jdownloader.captcha.v2.solver.jac.JACSolver;
import org.jdownloader.captcha.v2.solverjob.SolverJob;
import org.jdownloader.settings.staticreferences.CFG_CAPTCHA;
import org.jdownloader.settings.staticreferences.CFG_SILENTMODE;

public class DialogClickCaptchaSolver extends ChallengeSolver<ClickedPoint> {
    private CaptchaSettings    config;
    private Captcha9kwSettings config9kw;

    private DialogClickCaptchaSolver() {
        super(1);
        config = JsonConfig.create(CaptchaSettings.class);
        config9kw = JsonConfig.create(Captcha9kwSettings.class);
    }

    private static final DialogClickCaptchaSolver INSTANCE = new DialogClickCaptchaSolver();

    public static DialogClickCaptchaSolver getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean canHandle(Challenge<?> c) {
        return CFG_CAPTCHA.CAPTCHA_DIALOGS_ENABLED.isEnabled() && super.canHandle(c);
    }

    public void enqueue(SolverJob<ClickedPoint> solverJob) {
        if (solverJob.getChallenge() instanceof ClickCaptchaChallenge) {
            super.enqueue(solverJob);
        }

    }

    @Override
    public void solve(SolverJob<ClickedPoint> solverJob) throws InterruptedException, SkipException {
        synchronized (DialogBasicCaptchaSolver.getInstance()) {
            if (solverJob.getChallenge() instanceof ClickCaptchaChallenge && CFG_CAPTCHA.CAPTCHA_DIALOGS_ENABLED.isEnabled()) {
                solverJob.getLogger().info("Waiting for JAC (Click/Mouse)");
                solverJob.waitFor(config.getCaptchaDialogJAntiCaptchaTimeout(), JACSolver.getInstance());
                // StringUtils.isEmpty(config.getApiKey())
                if (config9kw.ismouse() && config.getCaptchaDialog9kwTimeout() > 0) solverJob.waitFor(config.getCaptchaDialog9kwTimeout(), Captcha9kwSolverClick.getInstance());

                solverJob.getLogger().info("JAC (Click/Mouse) is done. Response so far: " + solverJob.getResponse());
                if (JDGui.getInstance().isSilentModeActive()) {
                    switch (CFG_SILENTMODE.CFG.getOnCaptchaDuringSilentModeAction()) {
                    case DEFAULT_DIALOG_HANDLING:
                        break;
                    case DISABLE_DIALOG_SOLVER:
                        return;
                    case SKIP_LINK:
                        throw new SkipException(SkipRequest.SINGLE);
                    }
                }
                checkInterruption();
                ClickCaptchaChallenge captchaChallenge = (ClickCaptchaChallenge) solverJob.getChallenge();
                checkInterruption();
                ClickCaptchaDialogHandler handler = new ClickCaptchaDialogHandler(captchaChallenge);

                handler.run();

                if (handler.getPoint() != null) {
                    solverJob.addAnswer(new ClickCaptchaResponse(captchaChallenge, this, handler.getPoint(), 100));
                }
            }
        }

    }

}
