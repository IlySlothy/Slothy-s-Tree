package com.packhub.compat;

import java.util.Collection;
import net.minecraft.class_3283;

final class VersionCompatImpl {
   private VersionCompatImpl() {
   }

   static Collection<String> enabledNames(class_3283 manager) {
      return manager.method_29210();
   }

   static Collection<String> profileIds(class_3283 manager) {
      return manager.method_29206();
   }
}
