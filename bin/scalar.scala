// Copyright (C) GridGain Systems Licensed under GPLv3, http://www.gnu.org/licenses/gpl.html

/*
 * ________________________ ______ _______ ________
 * __  ___/__  ____/___    |___  / ___    |___  __ \
 * _____ \ _  /     __  /| |__  /  __  /| |__  /_/ /
 * ____/ / / /___   _  ___ |_  /____  ___ |_  _, _/
 * /____/  \____/   /_/  |_|/_____//_/  |_|/_/ |_|  
 *
 */

/*
 * Load script for Scala REPL.
 *
 * @author 2012 Copyright (C) GridGain Systems
 * @version 3.6.0c.13012012
 */

// Turn off ack-ing REPL commands.
:silent

// Add any of your imports here.
import org.gridgain.scalar.scalar
import org.gridgain.scalar.scalar._
import org.gridgain.scalar.lang._
import org.gridgain.grid._
import org.gridgain.grid.cache._
import org.gridgain.grid.lang._
import collection.JavaConversions._

// Import enums from Java for convenient usage.
import org.gridgain.grid.GridClosureCallMode._
import org.gridgain.grid.GridTaskSessionScope._
import org.gridgain.grid.GridJobResultPolicy._
import org.gridgain.grid.GridFactoryState._
import org.gridgain.grid.GridDeploymentMode._
import org.gridgain.grid.GridEventType._
import org.gridgain.grid.cache.GridCacheFlag._
import org.gridgain.grid.cache.GridCacheMode._
import org.gridgain.grid.cache.GridCachePeekMode._
import org.gridgain.grid.cache.GridCachePreloadMode._
import org.gridgain.grid.cache.GridCacheTxConcurrency._
import org.gridgain.grid.cache.GridCacheTxIsolation._
import org.gridgain.grid.cache.GridCacheTxState._

// Aliases
import org.gridgain.grid.{GridFactory => G}
import org.gridgain.grid.lang.{GridFunc => F}
import org.gridgain.grid.lang.{GridClosure => C1}
import org.gridgain.grid.lang.{GridClosure2 => C2}
import org.gridgain.grid.lang.{GridClosure3 => C3}
import org.gridgain.grid.lang.{GridClosureX => CX1}
import org.gridgain.grid.lang.{GridClosure2X => CX2}
import org.gridgain.grid.lang.{GridClosure3X => CX3}
import org.gridgain.grid.lang.{GridInClosure => CI1}
import org.gridgain.grid.lang.{GridInClosure2 => CI2}
import org.gridgain.grid.lang.{GridInClosure3 => CI3}
import org.gridgain.grid.lang.{GridInClosureX => CIX1}
import org.gridgain.grid.lang.{GridInClosure2X => CIX2}
import org.gridgain.grid.lang.{GridInClosure3X => CIX3}
import org.gridgain.grid.lang.{GridOutClosure => CO}
import org.gridgain.grid.lang.{GridOutClosureX => COX}
import org.gridgain.grid.lang.{GridAbsClosure => CA}
import org.gridgain.grid.lang.{GridAbsClosureX => CAX}
import org.gridgain.grid.lang.{GridAbsPredicate => PA}
import org.gridgain.grid.lang.{GridPredicate => P1}
import org.gridgain.grid.lang.{GridPredicate2 => P2}
import org.gridgain.grid.lang.{GridPredicate3 => P3}
import org.gridgain.grid.lang.{GridPredicateX => PX1}
import org.gridgain.grid.lang.{GridPredicate2X => PX2}
import org.gridgain.grid.lang.{GridPredicate3X => PX3}
import org.gridgain.grid.lang.{GridTuple => T1}
import org.gridgain.grid.lang.{GridTuple2 => T2}
import org.gridgain.grid.lang.{GridTuple3 => T3}
import org.gridgain.grid.lang.{GridTuple4 => T4}
import org.gridgain.grid.lang.{GridTuple5 => T5}

// Java global scope.
import org.gridgain.grid.typedef.X

// You can start any named grid.
// Default grid is started by default.
println()
scalar.logo()
println()
scalar.replHelp()
println()

// Turn back on ack-ing by REPL.
:silent



