package gamerguy11.sixtoolsaddon.accessor;

public interface InputAccessor {
   default float getMovementForward() {
      return 0.0F;
   }

   default void setMovementForward(float value) {
   }

   default float getMovementSideways() {
      return 0.0F;
   }

   default void setMovementSideways(float value) {
   }
}
