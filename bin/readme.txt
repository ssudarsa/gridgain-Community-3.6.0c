{GRIDGAIN_HOME}/bin README
--------------------------

ggstart.{sh|bat}
----------------
    This script allows to start grid node with either default configuration
    (if nothing is specified) or with specific configuration if configuration
    file is passed in.

ggjunit.{sh|bat}
----------------
    This script starts grid node with specific configuration for running Junits.
    Basically, it runs grid node with the following Spring XML configuration:
    config/junit/junit-spring.xml

ggscala.{sh|bat}
----------------
    This script starts Scala RELP with GridGain on the classpath. You can easily 
    load Scalar by executing command ":load bin/scalar.scala". This will load
    Scalar and join the default grid. 
    
    Note that you can freely modify script "bin/scalar.scala".

ggvisor.{sh|bat}
----------------
    This script starts Scala RELP with GridGain on the classpath and Visor loaded. 
    Visor gets loaded by loading Scala script "bin/visor.scala". 
    
    Note that you can freely modify script "bin/visor.scala" to load custom commands,
    pre-built scripts, etc.

setenv.{sh|bat}
---------------
    This script creates and exports GRIDGAIN_LIBS variable containing all the
    requires JARS for classpath for running grid node. Used by ggstart.{sh|bat}
    and ggjunit.{sh|bat} and can be used by user-created scripts as well.


