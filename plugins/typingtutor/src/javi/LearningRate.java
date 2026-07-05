package javi.typingtutor;

/**
 * Learning rate prediction using polynomial regression on per-key
 * speed samples.
 *
 * <p>Models typing speed improvement over time using linear or
 * quadratic regression on historical CPM measurements. Predicts
 * the number of additional sessions needed to reach the target
 * speed.</p>
 *
 * <p>Inspired by keybr.com's learning rate model which uses
 * polynomial regression to predict when the user will reach their
 * target speed.</p>
 */
final class LearningRate {

   /** Minimum data points for linear regression. */
   private static final int MIN_LINEAR_SAMPLES = 3;
   /** Minimum data points for quadratic regression. */
   private static final int MIN_QUADRATIC_SAMPLES = 6;
   /** Maximum sessions to predict into the future. */
   private static final int MAX_PREDICTION = 999;

   private final double[] cpmHistory;
   private final int count;

   /**
    * Creates a learning rate predictor from CPM history.
    *
    * @param cpmValues array of CPM values from oldest to newest
    * @param n number of valid entries in the array
    */
   LearningRate(double[] cpmValues, int n) {
      this.cpmHistory = cpmValues;
      this.count = n;
   }

   /**
    * Predicts the number of additional sessions needed to reach
    * the target CPM. Returns -1 if not enough data, 0 if already
    * at target, or MAX_PREDICTION if improvement rate is too slow.
    */
   int sessionsToTarget(double targetCpm) {
      if (count < MIN_LINEAR_SAMPLES)
         return -1;

      double currentCpm = cpmHistory[count - 1];
      if (currentCpm >= targetCpm)
         return 0;

      // Try quadratic fit first if enough data
      if (count >= MIN_QUADRATIC_SAMPLES) {
         int pred = predictWithQuadratic(targetCpm);
         if (pred >= 0)
            return pred;
      }

      // Fall back to linear
      return predictWithLinear(targetCpm);
   }

   /**
    * Returns the improvement rate in CPM per session (slope of the
    * linear regression). A positive value means improving; negative
    * means getting worse.
    */
   double improvementRate() {
      if (count < MIN_LINEAR_SAMPLES)
         return 0;
      double[] coeffs = linearRegression();
      return coeffs[1]; // slope
   }

   /**
    * Returns the R-squared goodness of fit for the linear model.
    * Values closer to 1.0 indicate the speed improvement is
    * consistent; values near 0 indicate erratic performance.
    */
   double consistency() {
      if (count < MIN_LINEAR_SAMPLES)
         return 0;
      double[] coeffs = linearRegression();
      return rSquared(coeffs);
   }

   /**
    * Predicts using linear regression (y = a + b*x).
    */
   private int predictWithLinear(double targetCpm) {
      double[] coeffs = linearRegression();
      double slope = coeffs[1];
      if (slope <= 0.01)
         return MAX_PREDICTION; // not improving

      // Solve: targetCpm = a + b * x  →  x = (target - a) / b
      double sessionsNeeded = (targetCpm - coeffs[0]) / slope;
      int remaining = (int) Math.ceil(sessionsNeeded) - count;
      if (remaining <= 0)
         return 1;
      return Math.min(remaining, MAX_PREDICTION);
   }

   /**
    * Predicts using quadratic regression (y = a + b*x + c*x^2).
    * Returns -1 if the quadratic model predicts no convergence.
    */
   private int predictWithQuadratic(double targetCpm) {
      double[] coeffs = quadraticRegression();
      if (coeffs == null)
         return -1;

      double a = coeffs[0], b = coeffs[1], c = coeffs[2];

      // If c < 0, improvement is decelerating (typical learning
      // curve). Search forward for when the predicted CPM >= target.
      for (int x = count; x < count + MAX_PREDICTION; x++) {
         double predicted = a + b * x + c * x * x;
         if (predicted >= targetCpm)
            return x - count;
         // If quadratic peaks below target and curves down, give up
         if (c < 0 && x > count + 10
               && predicted < a + b * (x - 1) + c * (x - 1) * (x - 1))
            return -1;
      }
      return -1;
   }

   /**
    * Ordinary least squares linear regression.
    *
    * @return [intercept, slope]
    */
   private double[] linearRegression() {
      double sumX = 0, sumY = 0, sumXX = 0, sumXY = 0;
      for (int i = 0; i < count; i++) {
         double x = i + 1;
         double y = cpmHistory[i];
         sumX += x;
         sumY += y;
         sumXX += x * x;
         sumXY += x * y;
      }
      double n = count;
      double denom = n * sumXX - sumX * sumX;
      if (Math.abs(denom) < 1e-10)
         return new double[]{sumY / n, 0};
      double slope = (n * sumXY - sumX * sumY) / denom;
      double intercept = (sumY - slope * sumX) / n;
      return new double[]{intercept, slope};
   }

   /**
    * Ordinary least squares quadratic regression.
    *
    * @return [a, b, c] for y = a + bx + cx^2, or null if singular
    */
   private double[] quadraticRegression() {
      // Normal equations for quadratic: solve 3x3 system
      double s0 = count;
      double s1 = 0, s2 = 0, s3 = 0, s4 = 0;
      double t0 = 0, t1 = 0, t2 = 0;
      for (int i = 0; i < count; i++) {
         double x = i + 1;
         double y = cpmHistory[i];
         double x2 = x * x;
         s1 += x;
         s2 += x2;
         s3 += x * x2;
         s4 += x2 * x2;
         t0 += y;
         t1 += x * y;
         t2 += x2 * y;
      }

      // Solve using Cramer's rule for 3x3 system:
      // [s0 s1 s2] [a]   [t0]
      // [s1 s2 s3] [b] = [t1]
      // [s2 s3 s4] [c]   [t2]
      double det = s0 * (s2 * s4 - s3 * s3)
                 - s1 * (s1 * s4 - s3 * s2)
                 + s2 * (s1 * s3 - s2 * s2);
      if (Math.abs(det) < 1e-10)
         return null;

      double a = (t0 * (s2 * s4 - s3 * s3)
                - s1 * (t1 * s4 - s3 * t2)
                + s2 * (t1 * s3 - s2 * t2)) / det;
      double b = (s0 * (t1 * s4 - s3 * t2)
                - t0 * (s1 * s4 - s3 * s2)
                + s2 * (s1 * t2 - t1 * s2)) / det;
      double c = (s0 * (s2 * t2 - t1 * s3)
                - s1 * (s1 * t2 - t1 * s2)
                + t0 * (s1 * s3 - s2 * s2)) / det;
      return new double[]{a, b, c};
   }

   /**
    * Computes R-squared for the linear model.
    */
   private double rSquared(double[] coeffs) {
      double mean = 0;
      for (int i = 0; i < count; i++)
         mean += cpmHistory[i];
      mean /= count;

      double ssTotal = 0, ssResidual = 0;
      for (int i = 0; i < count; i++) {
         double x = i + 1;
         double predicted = coeffs[0] + coeffs[1] * x;
         double residual = cpmHistory[i] - predicted;
         ssResidual += residual * residual;
         double deviation = cpmHistory[i] - mean;
         ssTotal += deviation * deviation;
      }
      if (ssTotal < 1e-10)
         return 1.0;
      return 1.0 - ssResidual / ssTotal;
   }
}
