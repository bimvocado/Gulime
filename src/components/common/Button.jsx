import React from 'react';

export default function Button({ children, onClick, variant = 'primary', className = '' }) {
    const baseStyle = "py-3.5 px-6 rounded-2xl font-bold text-sm transition-all flex items-center justify-center gap-2 active:scale-95 cursor-pointer";

    const variants = {
        primary: "bg-amber-300 hover:bg-amber-400 text-amber-950 shadow-md shadow-amber-200/60 font-black",
        secondary: "bg-amber-100 hover:bg-amber-200 text-amber-900 border border-amber-200/60",
        outline: "bg-white hover:bg-amber-50 text-amber-800 border-2 border-amber-200"
    };

    return (
        <button onClick={onClick} className={`${baseStyle} ${variants[variant]} ${className}`}>
            {children}
        </button>
    );
}