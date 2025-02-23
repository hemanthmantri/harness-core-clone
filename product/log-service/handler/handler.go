// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"io"
	"net/http"
	"net/http/pprof"

	"github.com/harness/harness-core/product/log-service/stackdriver"

	"github.com/harness/harness-core/product/platform/client"

	"github.com/go-chi/chi"

	gcputils "github.com/harness/harness-core/commons/go/lib/gcputils"
	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/metric"
	"github.com/harness/harness-core/product/log-service/queue"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/stream"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

// Handler returns an http.Handler that exposes the
// service resources.
func Handler(queue queue.Queue, cache cache.Cache, stream stream.Stream, store store.Store, stackdriver *stackdriver.Stackdriver, config config.Config, ngClient, aclClient *client.HTTPClient, gcsClient gcputils.GCS, metrics *metric.Metrics) http.Handler {
	r := chi.NewRouter()
	r.Use(logger.Middleware)

	// Token generation endpoints
	// Format: /token?accountID=
	r.Mount("/token", func() http.Handler {
		sr := chi.NewRouter()
		// Validate the incoming request with a global secret and return back a token
		// for the given account ID if the match is successful (if auth is enabled).
		if !config.Auth.DisableAuth {
			sr.Use(TokenGenerationMiddleware(config, true, ngClient))
		}

		sr.Get("/", HandleToken(config))
		return sr
	}()) // Validates against global token

	if config.Debug {
		// Log service info endpoints
		// Only accessible from Harness side (admin privileges) if debug mode in on
		// Format: /info
		r.Mount("/info", func() http.Handler {
			sr := chi.NewRouter()
			// Validate the incoming request with a global secret and return info only if the
			// match is successful. This endpoint should be only accessible from the Harness side.
			if !config.Auth.DisableAuth {
				sr.Use(TokenGenerationMiddleware(config, false, ngClient))
			}

			sr.Get("/stream", HandleInfo(stream))

			// Debug endpoints
			sr.HandleFunc("/debug/pprof/", pprof.Index)
			sr.HandleFunc("/debug/pprof/heap", pprof.Index)
			sr.HandleFunc("/debug/pprof/cmdline", pprof.Cmdline)
			sr.HandleFunc("/debug/pprof/profile", pprof.Profile)
			sr.HandleFunc("/debug/pprof/symbol", pprof.Symbol)
			sr.HandleFunc("/debug/pprof/trace", pprof.Trace)
			sr.HandleFunc("/debug/pprof/block", pprof.Index)
			sr.HandleFunc("/debug/pprof/goroutine", pprof.Index)
			sr.HandleFunc("/debug/pprof/threadcreate", pprof.Index)

			return sr
		}())
		r.Mount("/info/debug/getheap", pprof.Handler("heap"))
	}
	// mount and exposing the metrics to prometheus
	r.Mount("/metrics", promhttp.Handler())
	// Log stream endpoints
	// Format: /token?accountID=&key=
	r.Mount("/stream", func() http.Handler {
		sr := chi.NewRouter()
		// Validate the accountID in URL with the token generated above and authorize the request
		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient, aclClient, true))
		}

		sr.Post("/", HandleOpen(stream))
		sr.Delete("/", HandleClose(stream, store, config.Redis.ScanBatch))
		sr.Put("/", HandleWrite(stream, metrics))
		sr.Get("/", HandleTail(stream, metrics))
		sr.Get("/info", HandleInfo(stream))

		return sr
	}()) // Validates accountID with incoming token

	// Blob store endpoints
	// Format: /blob?accountID=&key=
	r.Mount("/blob", func() http.Handler {
		sr := chi.NewRouter()
		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient, aclClient, true))
		}
		sr.Post("/", HandleUpload(store, metrics))
		sr.Delete("/", HandleDelete(store))
		sr.Get("/", HandleDownload(store, metrics))
		sr.Post("/link/upload", HandleUploadLink(store))
		sr.Post("/link/download", HandleDownloadLink(store, metrics))
		sr.Get("/exists", HandleExists(store))

		return sr
	}())

	//Internal APIs
	r.Mount("/internal", func() http.Handler {
		sr := chi.NewRouter()
		// Validate the accountId in URL with the token generated above and authorize the request
		if !config.Auth.DisableAuth {
			sr.Use(AuthInternalMiddleware(config, true, ngClient))
		}
		sr.Delete("/blob", HandleInternalDelete(store))

		return sr
	}())

	if stackdriver != nil {
		// Stackdriver endpoints
		// Format: /stackdriver?accountID=&key=
		r.Mount("/stackdriver", func() http.Handler {
			sr := chi.NewRouter()
			if !config.Auth.DisableAuth {
				sr.Use(AuthMiddleware(config, ngClient, aclClient, false))
			}

			sr.Post("/", HandleStackDriverWrite(stackdriver))
			sr.Get("/", HandleStackdriverPing(stackdriver))
			return sr
		}())
	}

	// Log intelligence endpoints
	// Format: /rca?accountID=&key=
	r.Mount("/rca", func() http.Handler {
		sr := chi.NewRouter()
		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient, aclClient, true))
		}

		sr.Post("/", HandleRCA(store, config))
		return sr
	}())

	// Liveness check
	r.Get("/healthz", func(w http.ResponseWriter, r *http.Request) {
		io.WriteString(w, "OK")
	})

	// Readiness check
	r.Mount("/ready/healthz", func() http.Handler {
		sr := chi.NewRouter()
		sr.Get("/", HandlePing(cache, stream, store))

		return sr
	}())

	// Blob zip store endpoints
	// Format: /blob/download?accountID=&prefix=
	r.Mount("/blob/download", func() http.Handler {
		sr := chi.NewRouter()

		if !config.Auth.DisableAuth {
			sr.Use(AuthMiddleware(config, ngClient, aclClient, true))
		}

		sr.
			With(RequiredQueryParams(accountIDParam, usePrefixParam)).
			With(ValidatePrefixRequest()).
			With(CacheRequest(cache)).
			Post("/", HandleZipLinkPrefix(queue, store, cache, config, gcsClient, ngClient))

		return sr
	}())

	return r
}
