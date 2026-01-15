# kotlin2doxy.sed - Konwersja Kotlin -> Doxygen
s/\/\*\*/\/\*\*/g
s/\*\//\*\//g
s/\/\/\//\/\/\//g
s/@file/\@file/g
s/@brief/\@brief/g
s/@details/\@details/g
s/@param/\@param/g
s/@return/\@return/g
s/@throws/\@throws/g
s/@see/\@see/g
s/@pre/\@pre/g
s/@post/\@post/g
s/@code/\@code/g
s/@endcode/\@endcode/g