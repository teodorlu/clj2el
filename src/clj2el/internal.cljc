(ns clj2el.internal)

(declare transpile)

(defn add-local [env local]
  (update env :locals assoc local local))

(defn add-locals [env locals]
  (update env :locals merge (zipmap locals locals)))

(defn normalize-arg [arg]
  (if (= '& arg) '&rest arg))

(defn transpile-defn [[_defn name args & body] env]
  `(~'defun ~name ~(map normalize-arg args) ~@(map #(transpile % env) body)))

(defn transpile-let [[_let bindings & body] env]
  (let [[bindings env]
        (reduce (fn [[bindings env] [binding expr]]
                  [(conj bindings (list binding (transpile expr env))) (add-local env binding)])
                [[] env]
                (partition 2 bindings))]
    `(~'let* ~(sequence bindings)
             ~@(map #(transpile % env) body))))

(defn transpile-inc [[_let expr] env]
  `(~(symbol "1+") ~(transpile expr env)))

(defn transpile-map [[_map fn expr] env]
  `(~'mapcar ~(if (and (symbol? fn)
                       (not (get (:locals env) fn)))
                (transpile (list 'var fn) env)
                (transpile fn env))
             ~(transpile expr env)))

(defn transpile-fn [[_fn args & body] env]
  `(~'lambda ~(map normalize-arg args) ~@(map #(transpile % env) body)))

(defn transpile-var [[_var sym] _env]
  (symbol (str "#'" (case sym
                      inc (symbol "1+")
                      sym))))

(defn transpile-first [[_ arg] env]
  `(~'car ~(transpile arg env)))

(defn transpile-rest [[_ arg] env]
  `(~'cdr ~(transpile arg env)))

(defn transpile [form env]
  (if (seq? form)
    (case (first form)
      let (transpile-let form env)
      inc (transpile-inc form env)
      defn (transpile-defn form env)
      map (transpile-map form env)
      fn (transpile-fn form env)
      var (transpile-var form env)
      first (transpile-first form env)
      rest (transpile-rest form env)
      comment nil
      (sequence (map #(transpile % env) form)))
    form))


