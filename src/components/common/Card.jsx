import React from 'react';

export default function Card({ title, subtitle, icon, children, className = '', highlight = false }) {
    return (
        <div className={`bg-white rounded-[2rem] p-6 border transition-all ${
            highlight
                ? 'border-amber-300 shadow-xl shadow-amber-100/60 ring-2 ring-amber-200/50'
                : 'border-amber-100/80 shadow-sm hover:shadow-md hover:shadow-amber-100/40'
        } ${className}`}>
            {(title || icon) && (
                <div className="flex items-center gap-2.5 mb-5 pb-3 border-b border-amber-50">
                    {icon && <span className="text-xl">{icon}</span>}
                    <div>
                        {title && <h3 className="font-extrabold text-amber-950 text-base">{title}</h3>}
                        {subtitle && <p className="text-xs text-amber-700/60 font-medium">{subtitle}</p>}
                    </div>
                </div>
            )}
            {children}
        </div>
    );
}