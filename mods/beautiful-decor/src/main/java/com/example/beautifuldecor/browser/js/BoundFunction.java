package com.example.beautifuldecor.browser.js;

/** El resultado de {@code f.bind(...)}. */
final class BoundFunction extends JsFunction {
    final JsFunction target;
    final Object boundThis;
    final Object[] boundArgs;

    BoundFunction(Realm realm, JsFunction target, Object boundThis, Object[] boundArgs) {
        super(realm.functionProto);
        this.target = target;
        this.boundThis = boundThis;
        this.boundArgs = boundArgs;
        this.fname = "bound " + target.fname;
        this.flen = Math.max(0, target.flen - boundArgs.length);
    }

    private Object[] all(Object[] args) {
        if (this.boundArgs.length == 0) {
            return args;
        }
        Object[] r = new Object[this.boundArgs.length + args.length];
        System.arraycopy(this.boundArgs, 0, r, 0, this.boundArgs.length);
        System.arraycopy(args, 0, r, this.boundArgs.length, args.length);
        return r;
    }

    @Override
    public Object call(Object thiz, Object[] args) {
        return this.target.call(this.boundThis, all(args));
    }

    @Override
    public Object construct(Object[] args, JsObject newTarget) {
        return this.target.construct(all(args), newTarget == this ? this.target : newTarget);
    }

    @Override
    public boolean isConstructor() {
        return this.target.isConstructor();
    }
}
