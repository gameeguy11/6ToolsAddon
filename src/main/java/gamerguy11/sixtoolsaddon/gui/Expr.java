package gamerguy11.sixtoolsaddon.gui;

final class Expr {

   private final String s;
   private int pos;

   private Expr(String s) {
      this.s = s.replace(" ", "").replace(',', '.');
   }

   static double eval(String input) {
      final Expr e = new Expr(input);
      final double v = e.parseSum();
      if(e.pos != e.s.length()) {
         throw new IllegalArgumentException();
      }
      return v;
   }

   private double parseSum() {
      double v = this.parseProduct();
      while(this.pos < this.s.length()) {
         final char c = this.s.charAt(this.pos);
         if(c == '+') {
            this.pos++;
            v += this.parseProduct();
         } else if(c == '-') {
            this.pos++;
            v -= this.parseProduct();
         } else {
            break;
         }
      }
      return v;
   }

   private double parseProduct() {
      double v = this.parsePower();
      while(this.pos < this.s.length()) {
         final char c = this.s.charAt(this.pos);
         if(c == '*') {
            this.pos++;
            v *= this.parsePower();
         } else if(c == '/') {
            this.pos++;
            v /= this.parsePower();
         } else if(c == '%') {
            this.pos++;
            v %= this.parsePower();
         } else {
            break;
         }
      }
      return v;
   }

   private double parsePower() {
      final double base = this.parseUnary();
      if(this.pos < this.s.length() && this.s.charAt(this.pos) == '^') {
         this.pos++;
         return Math.pow(base, this.parsePower());
      }
      return base;
   }

   private double parseUnary() {
      if(this.pos < this.s.length()) {
         final char c = this.s.charAt(this.pos);
         if(c == '-') {
            this.pos++;
            return -this.parseUnary();
         }
         if(c == '+') {
            this.pos++;
            return this.parseUnary();
         }
      }
      return this.parseAtom();
   }

   private double parseAtom() {
      if(this.pos >= this.s.length()) {
         throw new IllegalArgumentException();
      }
      if(this.s.charAt(this.pos) == '(') {
         this.pos++;
         final double v = this.parseSum();
         if(this.pos >= this.s.length() || this.s.charAt(this.pos) != ')') {
            throw new IllegalArgumentException();
         }
         this.pos++;
         return v;
      }
      final int start = this.pos;
      while(this.pos < this.s.length() && (Character.isDigit(this.s.charAt(this.pos)) || this.s.charAt(this.pos) == '.')) {
         this.pos++;
      }
      if(start == this.pos) {
         throw new IllegalArgumentException();
      }
      return Double.parseDouble(this.s.substring(start, this.pos));
   }
}
