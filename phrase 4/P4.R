# --- WiFinder Phase 4: Complete clean analysis (stable fields + HMS time) ----
# Outputs:
#   - metrics_summary_<timestamp>.csv
#   - delta_baseline_vs_improved_<timestamp>.csv
#   - figs/: fig1_jitter.png, fig2_floorflips.png, fig3_success.png, fig4_latency.png
# Optional (toggles): fig5_latency_timeseries.png, fig7_rssi_vs_success.png

suppressPackageStartupMessages({
  library(readr); library(dplyr); library(tidyr)
  library(lubridate); library(ggplot2); library(purrr); library(stringr)
})

# --------------------------- USER SETTINGS ------------------------------------
base_dir <- "C:/Users/RichieC/Desktop/CS742/Assignment 2/assignment-2-app-group-04-nextleveldev/phrase 4"

files <- c(
  Baseline = file.path(base_dir, "wifi_eval_baseline_doubleoff.csv"),
  Improved = file.path(base_dir, "wifi_eval_baseline_doubleon.csv"),
  Variant  = file.path(base_dir, "wifi_eval_baselineonoff.csv")  # remove line if you don't want Variant
)

# Optional figure toggles
MAKE_LATENCY_TIMESERIES <- FALSE   # set TRUE to also make fig5_latency_timeseries.png
MAKE_RSSI_SCATTER       <- FALSE   # set TRUE if your CSVs contain an RSSI column

# --------------------------- OUTPUT FOLDERS -----------------------------------
out_dir <- file.path(base_dir, "phase4_out_R")
fig_dir <- file.path(out_dir, "figs")
dir.create(fig_dir, recursive = TRUE, showWarnings = FALSE)
ts_tag <- format(Sys.time(), "%Y%m%d_%H%M%S")  # avoid Windows file-lock issues

# --------------------------- HELPERS ------------------------------------------
find_col <- function(nms, aliases){
  ln <- tolower(nms)
  for (a in aliases){
    hit <- which(ln == tolower(a)); if (length(hit)>0) return(nms[hit[1]])
  }
  for (a in aliases){
    hit <- which(str_detect(ln, fixed(tolower(a)))); if (length(hit)>0) return(nms[hit[1]])
  }
  NA_character_
}

build_mapping <- function(df){
  nms <- names(df)
  list(
    timestamp    = find_col(nms, c("time","timestamp","ts","datetime")),
    x_stable     = find_col(nms, c("stx","stablex","x","rawx")),
    y_stable     = find_col(nms, c("sty","stabley","y","rawy")),
    floor_stable = find_col(nms, c("stfloor","stablefloor","floor","rawfloor")),
    attempts     = find_col(nms, c("attempts","attempt")),
    successes    = find_col(nms, c("successes","success")),
    successRate  = find_col(nms, c("successrate","success_rate")),
    latencyMs    = find_col(nms, c("avglatencyms","latencyms","latency_ms","scan_latency")),
    rssi         = find_col(nms, c("lastrssi","lastmaxrssi","maxrssi","rssi","signal"))
  )
}

# Parse "HH:MM:SS.mmm" into milliseconds since start of file
parse_hms_to_ms <- function(v){
  t <- suppressWarnings(parse_date_time(v, orders = c("HMS%OS3","HMS","HM")))
  if (all(is.na(t))) return(rep(NA_real_, length(v)))
  sec <- hour(t)*3600 + minute(t)*60 + second(t)
  idx <- which(is.finite(sec))
  out <- rep(NA_real_, length(v))
  if (length(idx) > 1) out[idx] <- (sec[idx] - sec[idx[1]]) * 1000
  out
}

normalize_df <- function(df){
  map <- build_mapping(df)
  req <- c("timestamp","x_stable","y_stable","floor_stable")
  for (r in req) if (is.na(map[[r]])) stop(sprintf("Missing column for '%s'", r))

  out <- tibble(
    timestamp_raw = df[[map$timestamp]],
    stableX       = df[[map$x_stable]],
    stableY       = df[[map$y_stable]],
    stableFloor   = df[[map$floor_stable]],
    attempts      = if (!is.na(map$attempts)) as.numeric(df[[map$attempts]]) else NA_real_,
    successes     = if (!is.na(map$successes)) as.numeric(df[[map$successes]]) else NA_real_,
    successRate   = if (!is.na(map$successRate)) as.numeric(df[[map$successRate]]) else NA_real_,
    latencyMs     = if (!is.na(map$latencyMs)) as.numeric(df[[map$latencyMs]]) else NA_real_,
    rssi          = if (!is.na(map$rssi))       as.numeric(df[[map$rssi]])       else NA_real_
  )

  out$timestamp_ms <- parse_hms_to_ms(out$timestamp_raw)
  out
}

read_run <- function(path, label){
  message("Reading: ", label, "  <-  ", path)
  raw <- readr::read_csv(path, show_col_types = FALSE)
  df  <- normalize_df(raw)
  df$label <- label
  df
}

# Metrics
cadence_sec <- function(df){
  ts <- df$timestamp_ms
  if (length(ts) < 2 || all(is.na(ts))) return(NA_real_)
  dt <- diff(ts)/1000; dt <- dt[dt>0 & is.finite(dt)]
  ifelse(length(dt)>0, median(dt), NA_real_)
}

success_rate <- function(df){
  if (!all(is.na(df$successes)) && !all(is.na(df$attempts))){
    att <- tail(na.omit(df$attempts),1); suc <- tail(na.omit(df$successes),1)
    return(list(rate=ifelse(att>0,100*suc/att,NA_real_), attempts=att, successes=suc))
  }
  if (!all(is.na(df$successRate))){
    return(list(rate=100*mean(df$successRate,na.rm=TRUE),
                attempts=NA_real_, successes=NA_real_))
  }
  list(rate=NA_real_, attempts=NA_real_, successes=NA_real_)
}

avg_latency <- function(df){
  x <- df$latencyMs; x <- x[is.finite(x)]
  if (length(x)==0) return(NA_real_)
  mean(x)
}

floor_flips <- function(df){
  flips <- if (nrow(df)>1) sum(df$stableFloor[-1] != df$stableFloor[-nrow(df)], na.rm=TRUE) else 0
  ts <- df$timestamp_ms
  per_min <- if (!all(is.na(ts)) && length(ts)>1){
    dur_min <- (tail(ts,1) - ts[1])/(1000*60)
    ifelse(dur_min>0, flips/dur_min, NA_real_)
  } else NA_real_
  per100 <- 100 * flips / max(1, nrow(df))
  list(per_min=per_min, per100=per100, flips=flips)
}

jitter_proxy <- function(df){
  if (nrow(df) < 5) return(NA_real_)
  # local jitter via sliding window std (w=5)
  w <- 5
  js <- purrr::map_dbl(1:(nrow(df)-w+1), function(i){
    sub <- df[i:(i+w-1), c("stableX","stableY")]
    sqrt(stats::sd(sub$stableX, na.rm=TRUE)^2 + stats::sd(sub$stableY, na.rm=TRUE)^2)
  })
  median(js, na.rm = TRUE) # robust central tendency
}

# --------------------------- LOAD & COMPUTE -----------------------------------
runs <- imap(files, ~read_run(.x, .y))

metrics <- imap_dfr(runs, function(df, lab){
  sr <- success_rate(df); fl <- floor_flips(df)
  tibble(
    label = lab,
    success_rate = sr$rate,
    attempts = sr$attempts,
    successes = sr$successes,
    avg_latency_ms = avg_latency(df),
    cadence_s = cadence_sec(df),
    floor_flips_per_min = fl$per_min,
    floor_flips_per_100samples = fl$per100,
    floor_flips_total = fl$flips,
    xy_jitter_px = jitter_proxy(df)
  )
})

# Save metrics table (timestamped to avoid lock)
metrics_path <- file.path(out_dir, paste0("metrics_summary_", ts_tag, ".csv"))
readr::write_csv(metrics, metrics_path)

# Delta table: Baseline vs Improved (if both exist)
if (all(c("Baseline","Improved") %in% metrics$label)){
  base <- dplyr::filter(metrics, label=="Baseline")
  impr <- dplyr::filter(metrics, label=="Improved")
  delta <- tibble(
    metric = c("success_rate(%)","avg_latency_ms","cadence_s",
               "floor_flips_per_min","xy_jitter_px"),
    baseline = c(base$success_rate, base$avg_latency_ms, base$cadence_s,
                 base$floor_flips_per_min, base$xy_jitter_px),
    improved = c(impr$success_rate, impr$avg_latency_ms, impr$cadence_s,
                 impr$floor_flips_per_min, impr$xy_jitter_px),
    delta = improved - baseline,
    delta_pct = c(
      (improved[1]-baseline[1])/baseline[1]*100,
      (improved[2]-baseline[2])/baseline[2]*100,
      (improved[3]-baseline[3])/baseline[3]*100,
      (improved[4]-baseline[4])/baseline[4]*100,
      (improved[5]-baseline[5])/baseline[5]*100
    )
  )
  delta_path <- file.path(out_dir, paste0("delta_baseline_vs_improved_", ts_tag, ".csv"))
  readr::write_csv(delta, delta_path)
}

print(metrics)
cat("Saved:\n  -", metrics_path, "\n  - figs/ (4 main figures)\n")

# --------------------------- PLOTTING (4 MAIN FIGS) ---------------------------
bar_fmt <- function(p, ylab){
  p + geom_col(width=0.6) +
    geom_text(aes(label=sprintf(ifelse(max(..y.., na.rm=TRUE) > 10, "%.1f", "%.2f"), ..y..)),
              stat="identity", vjust=-0.6, size=4) +
    labs(x=NULL, y=ylab) +
    theme_classic(base_size=14) +
    theme(axis.line=element_line(size=0.5))
}

# 1) Jitter box: build samples from sliding window for visual distribution
jitter_samples <- imap_dfr(runs, function(df, lab){
  w <- 5
  if (nrow(df) <= w) return(tibble(label=lab, jitter=NA_real_))
  js <- map_dbl(1:(nrow(df)-w), function(i){
    sub <- df[i:(i+w-1), ]
    sqrt(stats::sd(sub$stableX, na.rm=TRUE)^2 + stats::sd(sub$stableY, na.rm=TRUE)^2)
  })
  tibble(label=lab, jitter=js)
})
fig1 <- ggplot(jitter_samples, aes(x=label, y=jitter)) +
  geom_boxplot(outlier.shape = NA, width=0.5) +
  stat_summary(fun=mean, geom="point", shape=21, size=3) +
  labs(x=NULL, y="Jitter (px)") +
  theme_classic(base_size=14)
ggsave(file.path(fig_dir, "fig1_jitter.png"), fig1, width=6.2, height=4.2, dpi=180)

# 2) Floor flips (per minute if available, else per 100 samples)
use_per_min <- any(is.finite(metrics$floor_flips_per_min))
dfp <- if (use_per_min) {
  metrics |> select(label, value=floor_flips_per_min)
} else {
  metrics |> select(label, value=floor_flips_per_100samples)
}
ylab <- if (use_per_min) "Flips per minute" else "Flips per 100 samples"
fig2 <- ggplot(dfp, aes(x=label, y=value))
fig2 <- bar_fmt(fig2, ylab)
ggsave(file.path(fig_dir, "fig2_floorflips.png"), fig2, width=6.2, height=4.2, dpi=180)

# 3) Success rate (%)
fig3 <- ggplot(metrics, aes(x=label, y=success_rate))
fig3 <- bar_fmt(fig3, "%")
ggsave(file.path(fig_dir, "fig3_success.png"), fig3, width=6.2, height=4.2, dpi=180)

# 4) Average latency (ms)
fig4 <- ggplot(metrics, aes(x=label, y=avg_latency_ms))
fig4 <- bar_fmt(fig4, "ms")
ggsave(file.path(fig_dir, "fig4_latency.png"), fig4, width=6.2, height=4.2, dpi=180)

# --------------------------- OPTIONAL FIGURES ---------------------------------
if (MAKE_LATENCY_TIMESERIES){
  lat_ts <- imap_dfr(runs, function(df, lab){
    tibble(label=lab, t_s=df$timestamp_ms/1000, avgLatencyMs=as.numeric(df$latencyMs))
  }) |> filter(is.finite(t_s), is.finite(avgLatencyMs))
  if (nrow(lat_ts)>0){
    fig5 <- ggplot(lat_ts, aes(x=t_s, y=avgLatencyMs)) +
      geom_line() +
      facet_wrap(~label, scales="free_x", ncol=1) +
      labs(x="Time (s)", y="Average latency (ms)") +
      theme_classic(base_size=14)
    ggsave(file.path(fig_dir, "fig5_latency_timeseries.png"), fig5, width=7, height=8, dpi=180)
  }
}

if (MAKE_RSSI_SCATTER){
  rssi_df <- imap_dfr(runs, function(df, lab){
    if (!"rssi" %in% names(df)) return(tibble())
    tibble(label=lab, rssi=df$rssi,
           successRate = dplyr::coalesce(as.numeric(df$successRate),
                                         ifelse(!all(is.na(df$successes)) && !all(is.na(df$attempts)),
                                                as.numeric(df$successes)/pmax(1,as.numeric(df$attempts)),
                                                NA_real_))*100)
  }) |> filter(is.finite(rssi), is.finite(successRate))
  if (nrow(rssi_df)>0){
    fig7 <- ggplot(rssi_df, aes(x=rssi, y=successRate)) +
      geom_point(alpha=0.5, size=1.2) +
      geom_smooth(method="loess", se=FALSE) +
      facet_wrap(~label, ncol=1) +
      labs(x="Signal strength (dBm)", y="Success rate (%)") +
      theme_classic(base_size=14)
    ggsave(file.path(fig_dir, "fig7_rssi_vs_success.png"), fig7, width=6.2, height=8, dpi=180)
  }
}

cat("Done.\nFigures saved to: ", normalizePath(fig_dir),
    "\nMetrics table: ", metrics_path, "\n", sep="")
